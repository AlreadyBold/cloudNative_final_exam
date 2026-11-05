package com.alyak.userapi.prescription.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 처방내역 불러오기에 쓰는 간편인증 앱.
 *
 * codefLevel 은 CODEF 요청의 loginTypeLevel 값이다.
 * 사용자 휴대폰에 해당 앱의 인증서(예: 카카오톡 지갑의 카카오 인증서)가 있어야 한다.
 */
@Getter
@RequiredArgsConstructor
public enum SimpleAuthApp {
	/** 카카오톡 */
	KAKAO("1"),
	/** 네이버 */
	NAVER("6"),
	/** 토스 */
	TOSS("8");

	private final String codefLevel;
}
