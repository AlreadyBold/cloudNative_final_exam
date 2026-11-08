package com.alyak.userapi.prescription.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.prescription.config.PrescriptionImportProperties;

import lombok.RequiredArgsConstructor;

/**
 * 회원별 "하루 처방내역 조회 횟수" 제한 : 계정당 하루 최대 N번 (기본 5번).
 * CODEF 를 과도하게 호출하면 비용이 들고 기관이 IP 를 차단할 수 있어서 제한한다.
 *
 * <h3>왜 Redis 인가</h3>
 * 서버 메모리에 횟수를 세면 user-api 2대가 각자 따로 세서 제한이 2배로 풀린다.
 * 두 서버가 같은 Redis 숫자를 함께 센다.
 *
 * <pre>
 * 키  : alyak:codef:daily:&lt;userId&gt;:&lt;yyyyMMdd&gt;   (예: alyak:codef:daily:1:20261108, 한국 날짜)
 * 값  : 그날 실제로 조회한 횟수
 * TTL : 처음 셀 때 2일 (날짜가 키에 들어 있으므로 다음 날엔 새 키로 0부터 세고, 지난 키는 자동 삭제)
 * </pre>
 *
 * <h3>"한도 미만일 때만 1 증가"를 한 번에</h3>
 * 읽고(GET) 비교한 뒤 올리면(INCR), 두 요청이 동시에 와서 둘 다 4를 읽고 6번째가 통과할 수 있다.
 * Lua 스크립트는 Redis 안에서 한 번에(다른 명령이 끼어들 수 없게) 실행되므로 정확히 N번까지만 통과한다.
 * 거절된 요청은 숫자를 올리지 않는다.
 *
 * ※ 실제 CODEF 를 호출할 때만 센다. 샘플(MOCK) 모드는 CODEF 를 부르지 않으므로 세지 않는다.
 */
@Component
@RequiredArgsConstructor
public class ImportDailyLimiter {

	private static final String KEY_PREFIX = "alyak:codef:daily:";
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	/** 키를 보관할 시간 (초). 날짜가 바뀐 뒤 지난 키를 지우기 위함 */
	private static final String KEY_TTL_SECONDS = String.valueOf(2 * 24 * 60 * 60);

	/** 현재 값이 한도보다 작으면 1 올리고(처음이면 만료 시간 설정) 새 값을, 아니면 -1 을 돌려준다 */
	private static final RedisScript<Long> INCREASE_IF_BELOW = new DefaultRedisScript<>("""
		local current = tonumber(redis.call('GET', KEYS[1]) or '0')
		if current >= tonumber(ARGV[1]) then return -1 end
		local count = redis.call('INCR', KEYS[1])
		if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
		return count
		""", Long.class);

	private final StringRedisTemplate redisTemplate;
	private final PrescriptionImportProperties properties;

	/** 오늘 횟수를 1 늘린다. 이미 한도만큼 썼으면 429 (PRESCRIPTION_IMPORT_LIMIT_EXCEEDED) */
	public void increaseOrThrow(Long userId) {
		String today = LocalDate.now(KOREA).format(DateTimeFormatter.BASIC_ISO_DATE);
		Long result = redisTemplate.execute(INCREASE_IF_BELOW, List.of(KEY_PREFIX + userId + ":" + today),
			String.valueOf(properties.maxPerDay()), KEY_TTL_SECONDS);
		if (result == null || result < 0) {
			throw new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_LIMIT_EXCEEDED,
				"처방 내역 불러오기는 하루 최대 " + properties.maxPerDay() + "번까지 할 수 있습니다. 내일 다시 시도해 주세요.");
		}
	}
}
