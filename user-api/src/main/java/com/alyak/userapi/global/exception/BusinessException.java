package com.alyak.userapi.global.exception;

import lombok.Getter;

/**
 * 서비스 로직에서 "예상 가능한 실패"(중복 이메일, 비밀번호 불일치 등)를 알릴 때 던지는 예외.
 * GlobalExceptionHandler 가 받아서 ErrorCode 에 맞는 HTTP 상태와 ApiResponse 로 바꿔 응답한다.
 */
@Getter
public class BusinessException extends RuntimeException {

	/** 어떤 종류의 실패인지 */
	private final ErrorCode errorCode;

	/** 에러 코드의 기본 메시지를 사용 */
	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	/** 상황에 맞는 메시지를 직접 지정 */
	public BusinessException(ErrorCode errorCode, String message) {
		super(message);
		this.errorCode = errorCode;
	}
}
