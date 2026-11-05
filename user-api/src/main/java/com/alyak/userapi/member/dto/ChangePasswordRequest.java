package com.alyak.userapi.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 변경 요청 본문. 예) {"current_password": "old12345", "new_password": "new12345"}
 * (JSON 필드 이름은 snake_case 로 자동 변환된다)
 *
 * @param currentPassword 현재 비밀번호 (본인 확인용)
 * @param newPassword     새 비밀번호 (8~64자)
 */
public record ChangePasswordRequest(
	@NotBlank(message = "현재 비밀번호를 입력해 주세요.")
	String currentPassword,

	@NotBlank(message = "새 비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 64, message = "새 비밀번호는 8~64자로 입력해 주세요.")
	String newPassword
) {
}
