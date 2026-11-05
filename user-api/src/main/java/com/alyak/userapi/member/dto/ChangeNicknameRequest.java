package com.alyak.userapi.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 닉네임 변경 요청 본문. 예) {"nickname": "새이름"}
 *
 * @param nickname 바꿀 닉네임 (1~20자)
 */
public record ChangeNicknameRequest(
	@NotBlank(message = "닉네임을 입력해 주세요.")
	@Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
	String nickname
) {
}
