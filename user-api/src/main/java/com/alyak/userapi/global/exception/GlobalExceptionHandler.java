package com.alyak.userapi.global.exception;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.alyak.userapi.global.response.ApiResponse;

/**
 * 컨트롤러에서 발생한 예외를 한곳에서 잡아 공통 응답 형식(ApiResponse)으로 바꿔 주는 클래스.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 서비스에서 일부러 던진 예외 → 그 ErrorCode 그대로 응답 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException exception) {
		return toResponse(exception.getErrorCode(), exception.getMessage());
	}

	/** @Valid 검증 실패 → 어떤 필드가 왜 틀렸는지 메시지에 모아서 400 응답 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getDefaultMessage())
			.collect(Collectors.joining(" "));
		return toResponse(ErrorCode.COMMON_INVALID_INPUT, message);
	}

	/** JSON 형식 오류, 파라미터 누락·타입 오류 → 400 */
	@ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
		MissingServletRequestParameterException.class})
	public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
		return toResponse(ErrorCode.COMMON_INVALID_INPUT, ErrorCode.COMMON_INVALID_INPUT.getMessage());
	}

	/** 없는 주소 → 404 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
		return toResponse(ErrorCode.COMMON_NOT_FOUND, ErrorCode.COMMON_NOT_FOUND.getMessage());
	}

	/** 주소는 맞지만 메서드(GET/POST ...)가 다름 → 405 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
		return toResponse(ErrorCode.COMMON_METHOD_NOT_ALLOWED, ErrorCode.COMMON_METHOD_NOT_ALLOWED.getMessage());
	}

	/** 예상하지 못한 예외 → 내부 내용은 로그에만 남기고 응답에는 일반 메시지만 (정보 노출 방지) */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
		log.error("처리하지 못한 예외", exception);
		return toResponse(ErrorCode.COMMON_INTERNAL_ERROR, ErrorCode.COMMON_INTERNAL_ERROR.getMessage());
	}

	/** ErrorCode 의 HTTP 상태 + ApiResponse 실패 본문으로 응답 생성 */
	private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
		return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode, message));
	}
}
