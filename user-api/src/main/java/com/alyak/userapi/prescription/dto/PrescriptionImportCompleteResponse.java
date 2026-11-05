package com.alyak.userapi.prescription.dto;

import java.util.List;

import com.alyak.userapi.prescription.domain.PrescriptionDataSource;

/**
 * 처방내역 불러오기 완료 응답. (불러오기만 하고 저장은 하지 않는다. 저장은 pill-api 가 담당)
 *
 * @param source        CODEF(실제 데이터) / MOCK(샘플 데이터)
 * @param prescriptions 조제 내역 목록 (최근 1년)
 * @param servedBy      이 요청을 처리한 컨테이너
 */
public record PrescriptionImportCompleteResponse(
	PrescriptionDataSource source,
	List<ImportedPrescription> prescriptions,
	String servedBy
) {
}
