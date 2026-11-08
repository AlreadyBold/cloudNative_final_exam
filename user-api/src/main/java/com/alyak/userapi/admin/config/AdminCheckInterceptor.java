package com.alyak.userapi.admin.config;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.global.response.ApiResponse;
import com.alyak.userapi.global.session.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 관리자 API(/api/user/admin/**) 앞에서 "관리자인지"를 검사하는 인터셉터.
 *
 * <p>로그인 여부는 앞의 LoginCheckInterceptor 가 먼저 검사하므로, 여기서는 세션의 role 이 ADMIN 인지만 본다.
 * 일반 사용자가 주소를 직접 입력해 호출해도 403 으로 막는다 (화면에서 메뉴를 숨기는 것만으로는 부족하다).
 */
@Component
@RequiredArgsConstructor
public class AdminCheckInterceptor implements HandlerInterceptor {

	private final ObjectMapper objectMapper;

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
		throws Exception {
		LoginUser loginUser = LoginUser.from(request.getSession(false));
		if (loginUser != null && loginUser.isAdmin()) {
			return true;
		}
		ErrorCode errorCode = ErrorCode.COMMON_FORBIDDEN;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
		return false;
	}
}
