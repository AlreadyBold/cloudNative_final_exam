package com.alyak.userapi.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * ★ 서버 간 세션 공유 설정 (이 과제의 핵심).
 *
 * <h3>동작 원리</h3>
 * 기본 톰캣 세션은 컨테이너 "메모리"에 저장되므로 alyak-user-api-1 에서 로그인하면
 * alyak-user-api-2 는 그 세션을 모른다. Spring Session 은 HttpSession 의 저장 위치를
 * 공용 Redis(alyak-redis)로 바꿔서, 어느 컨테이너가 요청을 받아도 같은 세션을 읽게 한다.
 * 파이썬 서버(alyak-pill-api)도 같은 Redis 키를 직접 읽어 로그인 사용자를 확인한다.
 *
 * <h3>Redis 저장 형태</h3>
 * <pre>
 * 키   : alyak:session:sessions:&lt;세션ID&gt;      (Hash 자료형, 만료시간 = 세션 유효시간)
 * 필드 : creationTime, lastAccessedTime, maxInactiveInterval,
 *        sessionAttr:userId, sessionAttr:username, sessionAttr:nickname
 * 값   : JSON 문자열 (아래 springSessionDefaultRedisSerializer 가 만든다)
 * </pre>
 */
@Configuration
@EnableRedisHttpSession(
	// Redis 키 앞부분. 최종 키는 "alyak:session:sessions:<세션ID>" 가 된다
	redisNamespace = "alyak:session",
	// 마지막 요청 후 30분(1800초) 동안 요청이 없으면 세션 만료 (Redis 키도 자동 삭제)
	maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

	/** 세션 쿠키 이름. 파이썬 서버도 이 이름의 쿠키를 읽는다 */
	public static final String SESSION_COOKIE_NAME = "SESSION";

	/**
	 * 세션 값 직렬화 방식 : JDK 직렬화 대신 JSON.
	 *
	 * <p>기본값인 JDK 직렬화는 자바만 읽을 수 있는 바이너리라서 파이썬 서버가 해석할 수 없다.
	 * Spring Session 은 이 "빈 이름"(springSessionDefaultRedisSerializer)을 찾아서 직렬화에 사용한다.
	 * 그래서 메서드 이름을 반드시 이 이름으로 맞춰야 한다.
	 */
	@Bean
	public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
		return new GenericJackson2JsonRedisSerializer();
	}

	/**
	 * 세션 쿠키 설정.
	 *
	 * <ul>
	 *   <li>쿠키 이름 SESSION</li>
	 *   <li>path=/ : /api/user/** 뿐 아니라 /api/pill/** 요청에도 쿠키가 실려 가도록 사이트 전체에 적용</li>
	 *   <li>Base64 인코딩 끄기 : 기본값은 쿠키에 세션ID 를 Base64 로 바꿔 넣는다.
	 *       끄면 "쿠키 값 = Redis 키의 세션ID" 가 되어 파이썬이 쿠키 값으로 바로 Redis 를 조회할 수 있다.</li>
	 *   <li>HttpOnly : 자바스크립트에서 쿠키를 읽지 못하게 해서 XSS 로 세션이 탈취되는 것을 막는다</li>
	 *   <li>SameSite=Lax : 다른 사이트에서 보낸 POST 요청에는 쿠키를 싣지 않는다 (CSRF 완화)</li>
	 * </ul>
	 */
	@Bean
	public CookieSerializer cookieSerializer() {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SESSION_COOKIE_NAME);
		serializer.setCookiePath("/");
		serializer.setUseBase64Encoding(false);
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		return serializer;
	}
}
