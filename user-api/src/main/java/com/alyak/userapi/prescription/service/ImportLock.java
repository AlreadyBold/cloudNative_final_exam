package com.alyak.userapi.prescription.service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 회원 1명당 "처방내역 불러오기(간편인증)"를 동시에 1건만 진행하게 하는 잠금.
 *
 * <h3>왜 필요한가</h3>
 * 같은 아이디로 PC 2~3대에서 동시에 [불러오기]를 누르면 휴대폰에 인증 요청이 여러 번 가고 CODEF 호출도 낭비된다.
 * 가장 먼저 요청한 1대만 진행하고, 나머지는 CODEF 를 부르기 전에 서버가 바로 거절한다.
 *
 * <h3>동작</h3>
 * <pre>
 * 키  : alyak:codef:lock:&lt;userId&gt;   값 : &lt;세션ID&gt;|&lt;importId&gt;   TTL : 270초 (간편인증 제한시간)
 * </pre>
 * Redis 의 SET NX(키가 없을 때만 저장)는 한 번에 한 명령씩 처리되므로, 요청이 동시에 와도 딱 1개만 성공한다.
 * user-api 가 2대여도 같은 Redis 를 보므로 서버가 달라도 막힌다.
 * 끝나면(성공·실패) 바로 풀고, 응답 없이 끝나도 270초 뒤 Redis 가 자동으로 지운다.
 */
@Component
@RequiredArgsConstructor
public class ImportLock {

	private static final String KEY_PREFIX = "alyak:codef:lock:";
	private static final String SEPARATOR = "|";

	/**
	 * "값이 내 것일 때만 삭제" 를 Redis 안에서 한 번에 실행하는 스크립트.
	 * (GET 후 DEL 을 따로 하면 그 사이에 다른 요청이 잠금을 잡았을 때 남의 잠금을 지울 수 있다)
	 */
	private static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>(
		"if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
		Long.class);

	private final StringRedisTemplate redisTemplate;

	/** 지금 잠금을 가진 요청 정보 */
	public record Holder(String sessionId, String importId, long remainingSeconds) {
	}

	/**
	 * 잠금 시도. 비어 있으면 잡고 빈 값을 돌려준다. 이미 누가 잡고 있으면 그 정보를 돌려준다(잠금은 그대로).
	 */
	public Optional<Holder> tryAcquire(Long userId, String sessionId, String importId, Duration timeToLive) {
		String key = KEY_PREFIX + userId;
		Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value(sessionId, importId), timeToLive);
		if (Boolean.TRUE.equals(acquired)) {
			return Optional.empty();
		}
		String current = redisTemplate.opsForValue().get(key);
		if (current == null) {
			// 그 사이에 풀렸으면 한 번 더 시도
			return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value(sessionId, importId),
				timeToLive)) ? Optional.empty() : Optional.of(new Holder("", "", timeToLive.toSeconds()));
		}
		Long ttl = redisTemplate.getExpire(key);
		String[] parts = current.split("\\" + SEPARATOR, 2);
		return Optional.of(new Holder(parts[0], parts.length > 1 ? parts[1] : "", ttl == null ? 0 : Math.max(ttl, 0)));
	}

	/** 같은 세션이 다시 시작할 때 : 잠금을 새 importId 로 덮어쓴다 */
	public void overwrite(Long userId, String sessionId, String importId, Duration timeToLive) {
		redisTemplate.opsForValue().set(KEY_PREFIX + userId, value(sessionId, importId), timeToLive);
	}

	/** 내 잠금일 때만 푼다 */
	public void release(Long userId, String sessionId, String importId) {
		redisTemplate.execute(RELEASE_IF_OWNER, List.of(KEY_PREFIX + userId), value(sessionId, importId));
	}

	private static String value(String sessionId, String importId) {
		return sessionId + SEPARATOR + importId;
	}
}
