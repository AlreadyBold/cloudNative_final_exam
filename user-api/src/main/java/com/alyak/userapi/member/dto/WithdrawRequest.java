package com.alyak.userapi.member.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청 본문. 예) {"password": "password123"}
 * 실수로 탈퇴하는 것을 막기 위해 비밀번호를 한 번 더 확인한다.
 *
 * @param password 현재 비밀번호
 */
public record WithdrawRequest(
	@NotBlank(message = "비밀번호를 입력해 주세요.")
	String password
) {
}
