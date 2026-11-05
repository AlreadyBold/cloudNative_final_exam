package com.alyak.userapi.prescription.service;

import java.util.regex.Pattern;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;

/**
 * 휴대폰 번호 정규화 : "010-1234-5678", "+82 10 1234 5678" 등을 "01012345678" 로 맞춘다.
 * CODEF 요청 전에 거친다.
 */
public final class PhoneNumbers {

	/** 한국 휴대폰 번호 : 010/011/016/017/018/019 + 7~8자리 */
	private static final Pattern KOREAN_MOBILE = Pattern.compile("^01[016789]\\d{7,8}$");

	private PhoneNumbers() {
	}

	public static String normalize(String raw) {
		if (raw == null) {
			throw new BusinessException(ErrorCode.PHONE_INVALID_NUMBER);
		}
		// 숫자만 남긴다
		String digits = raw.replaceAll("\\D", "");
		// 국가번호(82)로 시작하면 0 으로 바꾼다
		if (digits.startsWith("82")) {
			digits = "0" + digits.substring(2);
		}
		if (!KOREAN_MOBILE.matcher(digits).matches()) {
			throw new BusinessException(ErrorCode.PHONE_INVALID_NUMBER);
		}
		return digits;
	}
}
