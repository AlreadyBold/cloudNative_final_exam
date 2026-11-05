package com.alyak.userapi.prescription.service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 추측할 수 없는 난수 문자열 생성.
 * 처방내역 불러오기의 importId 로 쓴다.
 */
final class RandomTokens {

	/** 암호학적으로 안전한 난수 생성기 */
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private RandomTokens() {
	}

	/** 32바이트(256비트) 난수를 URL 에 넣어도 안전한 Base64 문자열로 */
	static String opaqueToken() {
		byte[] bytes = new byte[32];
		SECURE_RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
