package com.alyak.userapi.global.response;

import com.alyak.userapi.global.exception.ErrorCode;

/**
 * 모든 API 의 공통 응답 형식.
 *
 * <pre>
 * 성공 : {"success": true,  "data": {...}, "error": null}
 * 실패 : {"success": false, "data": null,  "error": {"code": "AUTH_INVALID_CREDENTIALS", "message": "..."}}
 * </pre>
 *
 * 프론트는 HTTP 상태코드로 큰 분류(401, 404 ...)를 보고, 세부 처리는 error.code 로 분기한다.
 * 파이썬 서버(alyak-pill-api)도 같은 형식으로 응답해서 프론트 코드 하나로 두 서버를 다룰 수 있다.
 *
 * @param success 성공 여부
 * @param data    성공 시 결과 데이터 (실패 시 null)
 * @param error   실패 시 에러 정보 (성공 시 null)
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

	/** 결과 데이터가 있는 성공 응답 */
	public static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, null);
	}

	/** 결과 데이터가 없는 성공 응답 (로그아웃, 비밀번호 변경 등) */
	public static ApiResponse<Void> ok() {
		return new ApiResponse<>(true, null, null);
	}

	/** 에러 코드의 기본 메시지로 실패 응답 생성 */
	public static ApiResponse<Void> fail(ErrorCode errorCode) {
		return fail(errorCode, errorCode.getMessage());
	}

	/** 메시지를 직접 지정한 실패 응답 생성 (검증 실패 상세, CODEF 안내 문구 등) */
	public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
		return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message));
	}

	/**
	 * 실패 응답의 에러 정보.
	 *
	 * @param code    에러 코드 (ErrorCode enum 이름). 프론트는 이 값으로 분기한다
	 * @param message 사용자에게 그대로 보여 줄 수 있는 한국어 메시지
	 */
	public record ErrorBody(String code, String message) {
	}
}
