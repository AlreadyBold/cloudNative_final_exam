package com.alyak.userapi.prescription.service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.alyak.userapi.prescription.domain.PrescriptionDataSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * 간편인증 "대기 정보"를 Redis 에 잠깐(270초) 보관하는 저장소.
 *
 * <h3>왜 Redis 인가 (서버 2대 구성 대응)</h3>
 * user-api 가 2대이고 프록시가 요청을 번갈아 보내면,
 * "시작" 요청은 alyak-user-api-1 이, "완료" 요청은 alyak-user-api-2 가 받을 수 있다.
 * 메모리에 두면 2번 서버는 대기 정보를 몰라서 "인증 시간이 지났습니다" 오류가 난다.
 * 그래서 두 서버가 함께 보는 Redis 에 저장한다.
 *
 * <pre>
 * 키  : alyak:codef:pending:&lt;importId&gt;   (String 자료형, JSON)
 * TTL : 270초 (CODEF 추가인증 제한시간). 시간이 지나면 Redis 가 자동 삭제
 * </pre>
 *
 * 주민등록번호는 여기에도 저장하지 않는다. (완료 요청 때 프론트가 다시 보냄)
 */
@Component
@RequiredArgsConstructor
public class PendingImportStore {

	private static final String KEY_PREFIX = "alyak:codef:pending:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	/**
	 * 대기 중인 불러오기 1건.
	 *
	 * @param userId         시작한 회원 번호 (다른 회원이 importId 를 가로채 완료하지 못하게 확인용)
	 * @param source         CODEF(실제) / MOCK(샘플)
	 * @param baseParameters CODEF 1차 요청값 중 주민등록번호를 뺀 나머지 (이름, 휴대폰 번호 등)
	 * @param twoWayInfo     CODEF 가 준 추가인증 식별 정보 (jobIndex, threadIndex, jti, twoWayTimestamp)
	 */
	public record PendingImport(Long userId, PrescriptionDataSource source, Map<String, Object> baseParameters,
		Map<String, Object> twoWayInfo) {
	}

	/** 대기 정보 저장 (지정한 시간이 지나면 자동 삭제) */
	public void save(String importId, PendingImport pendingImport, Duration timeToLive) {
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + importId, objectMapper.writeValueAsString(pendingImport),
				timeToLive);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("간편인증 대기 정보를 JSON 으로 바꾸지 못했습니다.", exception);
		}
	}

	/** 대기 정보 조회. 없거나 만료됐으면 빈 값 */
	public Optional<PendingImport> find(String importId) {
		String json = redisTemplate.opsForValue().get(KEY_PREFIX + importId);
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, PendingImport.class));
		} catch (JsonProcessingException exception) {
			return Optional.empty();
		}
	}

	/** 완료했거나 더 이상 쓸 수 없는 대기 정보 삭제 */
	public void delete(String importId) {
		redisTemplate.delete(KEY_PREFIX + importId);
	}
}
