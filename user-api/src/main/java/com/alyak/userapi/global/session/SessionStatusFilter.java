package com.alyak.userapi.global.session;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.alyak.userapi.global.config.SessionConfig;
import com.alyak.userapi.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * GET /api/user/session/status : 세션 상태 확인 (프론트가 5초마다 부른다).
 *
 * <p>서버는 다른 기기 로그인 시 이전 세션을 즉시 지우지만, 브라우저는 다음 요청을 보낼 때까지 그 사실을 모른다.
 * 그래서 화면이 열려 있는 동안 이 주소로 가볍게 확인해서, 끊겼으면 새로고침 없이 로그인 화면으로 보낸다.
 *
 * <h3>★ 컨트롤러가 아니라 "필터"로 만든 이유</h3>
 * 세션을 한 번이라도 열면 Spring Session 이 마지막 접근 시각을 고쳐 30분 만료가 연장된다.
 * 5초마다 연장되면 화면만 켜 두어도 로그인이 영원히 끝나지 않는다.
 * Spring MVC 는 컨트롤러 코드와 상관없이 요청마다 세션을 열어 본다 (FlashMap 관리, 요청 처리 이벤트 등).
 * 그래서 Spring Session·Spring MVC 보다 앞에서 동작하는 필터가 쿠키 값만 읽어 Redis 를 직접 확인하고 바로 응답한다.
 *
 * <p>응답 status : ACTIVE(로그인 유지) / REPLACED(다른 기기에서 로그인되어 끊김) / NONE(로그아웃·만료).
 * 항상 200 으로 응답한다 (프론트가 401 처리 흐름을 타지 않고 직접 판단하도록).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1) // X-Served-By 헤더 필터(HIGHEST) 다음, Spring Session 필터보다 앞
@RequiredArgsConstructor
public class SessionStatusFilter extends OncePerRequestFilter {

	/** 이 필터가 직접 처리하는 주소 */
	public static final String PATH = "/api/user/session/status";

	private final LoginSessionRegistry loginSessionRegistry;
	private final ObjectMapper objectMapper;

	/** 다른 주소는 이 필터를 건너뛴다 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("GET".equals(request.getMethod()) && PATH.equals(request.getRequestURI()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		String sessionId = sessionCookie(request);
		String status;
		if (sessionId != null && loginSessionRegistry.sessionExists(sessionId)) {
			status = "ACTIVE";
		} else if (loginSessionRegistry.wasReplaced(sessionId)) {
			status = "REPLACED";
		} else {
			status = "NONE";
		}
		// 다음 필터(세션·MVC)로 넘기지 않고 여기서 응답을 끝낸다
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ApiResponse.ok(Map.of("status", status)));
	}

	/** SESSION 쿠키 값 (Spring Session 을 거치지 않고 직접 읽는다) */
	private static String sessionCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		return Arrays.stream(cookies)
			.filter(cookie -> SessionConfig.SESSION_COOKIE_NAME.equals(cookie.getName()))
			.map(Cookie::getValue)
			.findFirst()
			.orElse(null);
	}
}
