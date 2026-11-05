package com.alyak.userapi.prescription.dto;

import com.alyak.userapi.prescription.domain.PrescriptionDataSource;

/**
 * 처방내역 불러오기 시작 응답.
 *
 * @param importId         완료 API 주소에 넣을 ID (/api/user/prescriptions/import/{import_id}/complete)
 * @param expiresInSeconds 이 시간(초) 안에 휴대폰에서 승인하고 완료 API 를 호출해야 한다
 * @param source           CODEF(실제 인증 진행 중) / MOCK(샘플 모드, 휴대폰 승인 불필요)
 * @param servedBy         이 요청을 처리한 컨테이너 (완료 요청이 다른 컨테이너로 가도 되는지 시연할 때 확인용)
 */
public record PrescriptionImportStartResponse(
	String importId,
	long expiresInSeconds,
	PrescriptionDataSource source,
	String servedBy
) {
}
