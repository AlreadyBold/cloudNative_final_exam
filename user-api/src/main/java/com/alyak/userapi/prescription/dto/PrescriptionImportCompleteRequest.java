package com.alyak.userapi.prescription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 처방내역 불러오기 완료 본문. 예) {"identity": "900101-1234567"}
 *
 * <p>CODEF 2차 요청에는 1차 요청값(주민등록번호 포함)이 다시 필요하다.
 * 주민등록번호를 서버(Redis 포함)에 보관하지 않으려고 프론트가 한 번 더 보낸다.
 *
 * @param identity 1차 요청과 같은 주민등록번호
 */
public record PrescriptionImportCompleteRequest(
	@NotBlank(message = "주민등록번호를 입력해 주세요.")
	@Pattern(regexp = IDENTITY_PATTERN, message = "주민등록번호 13자리를 입력해 주세요.")
	String identity
) {

	/** 주민등록번호 형식 : 숫자 6자리 + (하이픈 선택) + 숫자 7자리 */
	public static final String IDENTITY_PATTERN = "^\\d{6}-?\\d{7}$";
}
