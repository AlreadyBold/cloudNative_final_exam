package com.alyak.userapi.member.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties 의 admin.* 설정. 값은 env/user-api.env 의 ADMIN_EMAIL, ADMIN_PASSWORD 에서 온다.
 * 비밀번호를 코드·이미지·git 에 두지 않기 위해 환경변수로만 받는다.
 *
 * @param email    관리자 로그인 이메일
 * @param password 관리자 비밀번호 (원문. 저장할 때는 BCrypt 해시로 바꾼다)
 */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String email, String password) {

	/** 이메일·비밀번호가 모두 있어야 관리자 계정을 만든다 */
	public boolean isConfigured() {
		return email != null && !email.isBlank() && password != null && !password.isBlank();
	}
}
