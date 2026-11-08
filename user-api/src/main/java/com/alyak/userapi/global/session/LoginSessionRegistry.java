package com.alyak.userapi.global.session;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 계정당 로그인 세션을 1개만 허용한다 (중복 로그인 방지, "나중 로그인 우선").
 *
 * <h3>동작</h3>
 * <pre>
 * alyak:login:user:&lt;userId&gt;        → 지금 로그인된 세션 ID   (계정 1개에 세션 1개)
 * alyak:login:ended:&lt;세션ID&gt;       → 끊긴 이유 (30분)       (REPLACED 다른 기기 로그인 / PASSWORD_RESET 비밀번호 초기화)
 * </pre>
 * <ol>
 *   <li>로그인하면 이 계정의 이전 세션 ID 를 새 ID 로 바꾸고, 이전 세션은 Redis 에서 지운다.
 *       → 이전 기기는 다음 요청부터 로그인이 풀린다 (user-api 2대, pill-api 모두 같은 Redis 를 보므로 동시에).</li>
 *   <li>끊긴 세션 ID 에는 끊긴 이유를 남겨서, 그 기기에 이유를 알려 줄 수 있게 한다.</li>
 *   <li>로그아웃·탈퇴하면 이 계정의 세션 기록을 지운다 (내 세션일 때만).</li>
 * </ol>
 * 먼저 로그인한 쪽을 우선하면, 로그아웃 없이 창을 닫았을 때 세션이 만료될 때까지(30분) 아무도 로그인할 수 없어서
 * 나중 로그인을 우선한다.
 */
@Component
@RequiredArgsConstructor
public class LoginSessionRegistry {

	private static final String USER_KEY_PREFIX = "alyak:login:user:";
	private static final String ENDED_KEY_PREFIX = "alyak:login:ended:";
	/** 끊긴 이유 : 같은 계정으로 다른 기기에서 로그인 */
	public static final String REASON_REPLACED = "REPLACED";
	/** 끊긴 이유 : 관리자가 비밀번호를 초기화 */
	public static final String REASON_PASSWORD_RESET = "PASSWORD_RESET";
	/** Spring Session 이 세션을 저장하는 Redis 키 (SessionConfig 의 redisNamespace + ":sessions:") */
	private static final String SESSION_KEY_PREFIX = "alyak:session:sessions:";
	/** 끊긴 이유를 남겨 둘 시간 (세션 유효시간과 같은 30분) */
	private static final Duration ENDED_MARK_TTL = Duration.ofMinutes(30);

	/** 값이 내 세션 ID 일 때만 지우는 스크립트 (다른 기기가 그 사이 로그인했다면 지우지 않는다) */
	private static final RedisScript<Long> DELETE_IF_SAME = new DefaultRedisScript<>(
		"if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
		Long.class);

	private final StringRedisTemplate redisTemplate;
	/** Spring Session 의 세션 저장소 (Redis). 이전 세션을 지울 때 사용 */
	private final SessionRepository<?> sessionRepository;

	/**
	 * 로그인 성공 시 호출. 이 계정의 이전 세션이 있으면 끊는다.
	 *
	 * @param userId    로그인한 회원
	 * @param sessionId 새로 발급된 세션 ID
	 */
	public void register(Long userId, String sessionId) {
		// GETSET : 새 세션 ID 를 저장하면서 이전 값을 한 번에 받아 온다 (두 기기가 동시에 로그인해도 하나만 남음)
		String previous = redisTemplate.opsForValue().getAndSet(USER_KEY_PREFIX + userId, sessionId);
		if (previous != null && !previous.equals(sessionId)) {
			endSession(previous, REASON_REPLACED);
		}
	}

	/** 로그아웃·탈퇴 시 호출. 지금 기록된 세션이 내 세션일 때만 지운다 */
	public void unregister(Long userId, String sessionId) {
		redisTemplate.execute(DELETE_IF_SAME, List.of(USER_KEY_PREFIX + userId), sessionId);
	}

	/**
	 * 이 계정의 로그인을 끊는다 (관리자가 비밀번호를 초기화할 때 사용)
	 *
	 * @param reason 끊긴 이유 (그 기기에 보여 줄 안내를 고르는 데 쓴다)
	 */
	public void forceLogout(Long userId, String reason) {
		String current = redisTemplate.opsForValue().getAndDelete(USER_KEY_PREFIX + userId);
		if (current != null) {
			endSession(current, reason);
		}
	}

	/** 세션을 지우고 끊긴 이유를 남긴다 */
	private void endSession(String sessionId, String reason) {
		sessionRepository.deleteById(sessionId);
		redisTemplate.opsForValue().set(ENDED_KEY_PREFIX + sessionId, reason, ENDED_MARK_TTL);
	}

	/** 지금 로그인 중인 세션 ID (없으면 null). 세션이 만료됐으면 null */
	public String currentSessionId(Long userId) {
		String sessionId = redisTemplate.opsForValue().get(USER_KEY_PREFIX + userId);
		return sessionId != null && sessionRepository.findById(sessionId) != null ? sessionId : null;
	}

	/** 세션이 Redis 에 살아 있는지 (세션을 열지 않으므로 만료 시간이 연장되지 않는다) */
	public boolean sessionExists(String sessionId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(SESSION_KEY_PREFIX + sessionId));
	}

	/** 이 세션이 강제로 끊겼다면 그 이유 (REPLACED / PASSWORD_RESET), 아니면 null */
	public String endReason(String sessionId) {
		return sessionId == null ? null : redisTemplate.opsForValue().get(ENDED_KEY_PREFIX + sessionId);
	}
}
