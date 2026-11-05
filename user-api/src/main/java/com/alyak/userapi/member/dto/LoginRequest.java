package com.alyak.userapi.member.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 본문. 예) {"email": "a@b.com", "password": "password123"}
 *
 * @param email    가입한 이메일
 * @param password 비밀번호
 */
public record LoginRequest(
	@NotBlank(message = "이메일을 입력해 주세요.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	String password
) {
}
