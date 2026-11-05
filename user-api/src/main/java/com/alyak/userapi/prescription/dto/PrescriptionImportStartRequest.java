package com.alyak.userapi.prescription.dto;

import com.alyak.userapi.prescription.domain.SimpleAuthApp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 처방내역 불러오기 시작(간편인증 요청) 본문.
 * 예) {"auth_app": "KAKAO", "user_name": "홍길동", "identity": "900101-1234567", "phone_number": "010-1234-5678"}
 *
 * @param authApp     간편인증 앱 (KAKAO / NAVER / TOSS)
 * @param userName    이름 (간편인증서의 실명)
 * @param identity    주민등록번호 13자리 (하이픈 허용). 서버에 저장하지 않는다
 * @param phoneNumber 간편인증서에 등록된 휴대폰 번호
 */
public record PrescriptionImportStartRequest(
	@NotNull(message = "간편인증 앱을 선택해 주세요.")
	SimpleAuthApp authApp,

	@NotBlank(message = "이름을 입력해 주세요.")
	@Size(max = 20, message = "이름은 20자 이하로 입력해 주세요.")
	String userName,

	@NotBlank(message = "주민등록번호를 입력해 주세요.")
	@Pattern(regexp = PrescriptionImportCompleteRequest.IDENTITY_PATTERN, message = "주민등록번호 13자리를 입력해 주세요.")
	String identity,

	@NotBlank(message = "휴대폰 번호를 입력해 주세요.")
	String phoneNumber
) {
}
