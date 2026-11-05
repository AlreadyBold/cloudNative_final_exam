package com.alyak.userapi.global.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.alyak.userapi.global.session.LoginCheckInterceptor;
import com.alyak.userapi.global.session.LoginUserArgumentResolver;

import lombok.RequiredArgsConstructor;

/**
 * Spring MVC 설정 : 로그인 검사 인터셉터와 LoginUser 파라미터 리졸버를 등록한다.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

	private final LoginCheckInterceptor loginCheckInterceptor;
	private final LoginUserArgumentResolver loginUserArgumentResolver;

	/**
	 * /api/user/** 전체에 로그인 검사를 걸고, 로그인 없이 써야 하는 주소만 뺀다.
	 */
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(loginCheckInterceptor)
			.addPathPatterns("/api/user/**")
			.excludePathPatterns(
				"/api/user/signup",   // 회원가입
				"/api/user/login",    // 로그인
				"/api/user/logout",   // 로그아웃 (이미 로그아웃된 상태에서 눌러도 성공으로 처리)
				"/api/user/health");  // 서버 상태 확인 (프록시·시연용)
	}

	/** 컨트롤러 파라미터 LoginUser 자동 주입 기능 등록 */
	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(loginUserArgumentResolver);
	}
}
