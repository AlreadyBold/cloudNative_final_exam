package com.alyak.userapi.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청 본문. 예) {"email": "a@b.com", "password": "password123", "nickname": "준형"}
 *
 * @param email    로그인 아이디로 쓸 이메일
 * @param password 비밀번호 (8~64자)
 * @param nickname 화면 표시 이름 (1~20자)
 */
public record SignupRequest(
	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 100, message = "이메일은 100자 이하로 입력해 주세요.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 64, message = "비밀번호는 8~64자로 입력해 주세요.")
	String password,

	@NotBlank(message = "닉네임을 입력해 주세요.")
	@Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
	String nickname
) {
}
