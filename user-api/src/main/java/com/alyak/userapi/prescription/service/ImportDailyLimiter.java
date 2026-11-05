package com.alyak.userapi.prescription.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.prescription.config.PrescriptionImportProperties;

import lombok.RequiredArgsConstructor;

/**
 * 회원별 "하루 처방내역 조회 횟수" 제한. (CODEF 를 과도하게 호출하면 기관이 IP 를 차단할 수 있음)
 *
 * <p>서버 메모리에 횟수를 세면 서버가 2대일 때 각자 따로 세서 제한이 2배로 풀린다.
 * 그래서 Redis 의 INCR(원자적 1 증가) 명령으로 두 서버가 같은 숫자를 함께 센다.
 *
 * <pre>
 * 키  : alyak:codef:daily:&lt;userId&gt;:&lt;yyyyMMdd&gt;   (예: alyak:codef:daily:1:20261104)
 * 값  : 오늘 시작한 횟수
 * TTL : 처음 셀 때 2일로 설정 (날짜가 키에 들어 있으므로 다음 날엔 새 키를 쓰고, 지난 키는 자동 삭제)
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ImportDailyLimiter {

	private static final String KEY_PREFIX = "alyak:codef:daily:";
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	private final StringRedisTemplate redisTemplate;
	private final PrescriptionImportProperties properties;

	/** 오늘 횟수를 1 늘리고, 한도를 넘으면 예외 */
	public void increaseOrThrow(Long userId) {
		String today = LocalDate.now(KOREA).format(DateTimeFormatter.BASIC_ISO_DATE);
		String key = KEY_PREFIX + userId + ":" + today;
		Long count = redisTemplate.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redisTemplate.expire(key, Duration.ofDays(2));
		}
		if (count != null && count > properties.maxPerDay()) {
			throw new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_LIMIT_EXCEEDED);
		}
	}
}
