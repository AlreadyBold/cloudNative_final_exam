package com.alyak.userapi.global.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 에러 코드 목록.
 *
 * <p>규칙 : 도메인별 접두어를 붙인다. COMMON_ 공통, AUTH_ 로그인, MEMBER_ 회원, PRESCRIPTION_ 처방내역.
 * 각 코드는 HTTP 상태코드와 사용자에게 보여 줄 한국어 메시지를 함께 가진다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// ----- 공통 -----
	COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
	COMMON_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	COMMON_FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다."),
	COMMON_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 주소를 찾을 수 없습니다."),
	COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
	COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

	// ----- 로그인 / 회원가입 -----
	AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
	AUTH_SESSION_REPLACED(HttpStatus.UNAUTHORIZED, "다른 기기에서 로그인되어 로그아웃되었습니다."),
	AUTH_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),

	// ----- 회원 (마이페이지) -----
	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	ADMIN_RESET_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "관리자 계정의 비밀번호는 초기화할 수 없습니다."),
	MEMBER_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않습니다."),

	// ----- 처방내역 불러오기 (CODEF 간편인증) -----
	PHONE_INVALID_NUMBER(HttpStatus.BAD_REQUEST, "휴대폰 번호 형식이 올바르지 않습니다."),
	PRESCRIPTION_IMPORT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "지금은 처방 내역을 불러올 수 없습니다. 잠시 후 다시 시도해 주세요."),
	PRESCRIPTION_IMPORT_IN_PROGRESS(HttpStatus.CONFLICT, "다른 기기에서 처방 내역 불러오기가 진행 중입니다."),
	PRESCRIPTION_IMPORT_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "오늘 처방 내역 불러오기 횟수를 모두 사용했습니다."),
	PRESCRIPTION_IMPORT_EXPIRED(HttpStatus.BAD_REQUEST, "인증 시간이 지났습니다. 처음부터 다시 진행해 주세요."),
	PRESCRIPTION_AUTH_NOT_APPROVED(HttpStatus.BAD_REQUEST, "간편인증 앱에서 인증을 먼저 완료해 주세요."),
	PRESCRIPTION_IDENTITY_MISMATCH(HttpStatus.BAD_REQUEST, "이름, 주민등록번호, 휴대폰 번호가 인증 정보와 일치하지 않습니다."),
	PRESCRIPTION_IMPORT_FAILED(HttpStatus.BAD_GATEWAY, "처방 내역을 불러오지 못했습니다.");

	/** 응답에 쓸 HTTP 상태코드 */
	private final HttpStatus status;
	/** 기본 안내 메시지 */
	private final String message;
}
