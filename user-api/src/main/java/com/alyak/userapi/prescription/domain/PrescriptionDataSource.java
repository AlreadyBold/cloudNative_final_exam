package com.alyak.userapi.prescription.domain;

/**
 * 처방내역을 어디서 가져왔는지.
 * 프론트는 MOCK 이면 "샘플 데이터" 배지를 보여 주고, 저장할 때도 이 값을 함께 저장한다.
 */
public enum PrescriptionDataSource {
	/** 실제 CODEF 간편인증 + 심평원 조회 결과 */
	CODEF,
	/** CODEF 키가 없을 때 돌려주는 샘플 데이터 */
	MOCK
}
