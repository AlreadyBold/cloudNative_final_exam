package com.alyak.userapi.global.session;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 컨트롤러 메서드의 {@code LoginUser} 타입 파라미터에 세션의 로그인 사용자를 자동으로 넣어 준다.
 *
 * <pre>
 * &#64;GetMapping("/me")
 * public ApiResponse&lt;...&gt; me(LoginUser loginUser) { ... }   // ← 여기에 자동 주입
 * </pre>
 *
 * 컨트롤러마다 session.getAttribute(...) 를 반복하지 않아도 되도록 만든 공통 기능이다.
 */
@Component
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

	/** 파라미터 타입이 LoginUser 일 때만 이 리졸버가 동작한다 */
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return LoginUser.class.equals(parameter.getParameterType());
	}

	/** 세션에서 LoginUser 를 꺼내 파라미터 값으로 돌려준다 */
	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		LoginUser loginUser = LoginUser.from(request == null ? null : request.getSession(false));
		// LoginCheckInterceptor 가 먼저 막아 주지만, 경로 설정 실수에 대비해 한 번 더 확인
		if (loginUser == null) {
			throw new BusinessException(ErrorCode.COMMON_UNAUTHORIZED);
		}
		return loginUser;
	}
}
