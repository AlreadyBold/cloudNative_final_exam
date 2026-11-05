package com.alyak.userapi.global.web;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 모든 응답에 {@code X-Served-By: <컨테이너 hostname>} 헤더를 붙이는 필터.
 *
 * <p>프록시가 user-api 2대에 요청을 번갈아 보내므로, 브라우저 개발자도구나 curl -i 로
 * 이 헤더를 보면 어느 컨테이너가 처리했는지 바로 알 수 있다. (세션 공유 검증용)
 *
 * <p>가장 먼저 실행되도록 순서를 최상위로 두고, 컨트롤러 실행 "전"에 헤더를 넣어서
 * 401 같은 에러 응답에도 빠짐없이 붙게 했다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ServedByHeaderFilter extends OncePerRequestFilter {

	/** 응답 헤더 이름 (파이썬 서버도 같은 이름 사용) */
	public static final String HEADER_NAME = "X-Served-By";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
		throws ServletException, IOException {
		response.setHeader(HEADER_NAME, ServerIdentity.hostname());
		chain.doFilter(request, response);
	}
}
