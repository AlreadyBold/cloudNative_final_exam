package com.alyak.userapi.global.session;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.global.response.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * 로그인이 필요한 API 앞에서 "로그인했는지"를 검사하는 인터셉터.
 *
 * <p>Spring Security 의 세션 로그인은 세션에 SecurityContext 객체를 통째로 저장해서
 * 파이썬 서버가 읽기 어렵기 때문에, 세션에 단순 값(userId 등)만 넣고 이 인터셉터로 직접 검사한다.
 *
 * <p>검사 대상 경로는 WebMvcConfig 에서 지정한다. (회원가입·로그인·로그아웃·상태확인은 제외)
 */
@Component
@RequiredArgsConstructor
public class LoginCheckInterceptor implements HandlerInterceptor {

	private final ObjectMapper objectMapper;
	private final LoginSessionRegistry loginSessionRegistry;

	/**
	 * 컨트롤러 실행 "전"에 호출된다. true 면 계속 진행, false 면 여기서 응답을 끝낸다.
	 */
	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
		throws Exception {
		// getSession(false) : 세션이 없으면 새로 만들지 않고 null (로그인 안 한 사용자에게 빈 세션을 만들지 않기 위함)
		LoginUser loginUser = LoginUser.from(request.getSession(false));
		if (loginUser != null) {
			return true;
		}

		// 로그인하지 않았으면 401 + 공통 응답 형식으로 거절
		// 쿠키의 세션이 강제로 끊긴 세션이면 그 이유를 알려 준다 (다른 기기 로그인 / 비밀번호 초기화)
		String reason = loginSessionRegistry.endReason(request.getRequestedSessionId());
		ErrorCode errorCode = LoginSessionRegistry.REASON_REPLACED.equals(reason) ? ErrorCode.AUTH_SESSION_REPLACED
			: LoginSessionRegistry.REASON_PASSWORD_RESET.equals(reason) ? ErrorCode.AUTH_PASSWORD_RESET
			: ErrorCode.COMMON_UNAUTHORIZED;
		response.setStatus(errorCode.getStatus().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
		return false;
	}
}
