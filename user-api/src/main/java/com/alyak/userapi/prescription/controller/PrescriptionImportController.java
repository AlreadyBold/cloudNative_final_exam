package com.alyak.userapi.prescription.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alyak.userapi.global.response.ApiResponse;
import com.alyak.userapi.global.session.LoginUser;
import com.alyak.userapi.global.web.ServerIdentity;
import com.alyak.userapi.prescription.dto.PrescriptionImportCompleteRequest;
import com.alyak.userapi.prescription.dto.PrescriptionImportCompleteResponse;
import com.alyak.userapi.prescription.dto.PrescriptionImportStartRequest;
import com.alyak.userapi.prescription.dto.PrescriptionImportStartResponse;
import com.alyak.userapi.prescription.service.PrescriptionImportService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 처방내역 불러오기 API (간편인증 2단계). 로그인 필요.
 * 회원은 세션의 LoginUser 로 식별한다.
 */
@RestController
@RequestMapping("/api/user/prescriptions/import")
@RequiredArgsConstructor
public class PrescriptionImportController {

	private final PrescriptionImportService importService;

	/**
	 * 1단계 : 간편인증 요청 보내기.
	 * 같은 아이디로 다른 기기에서 이미 진행 중이면 409 (PRESCRIPTION_IMPORT_IN_PROGRESS).
	 * 같은 브라우저에서 다시 누르면 이전 요청을 버리고 새로 시작한다.
	 * 실제 모드면 사용자의 카카오톡 등으로 인증 요청이 가고, 모의 모드면 바로 다음 단계로 진행할 수 있다.
	 * CODEF 응답을 기다리므로 수십 초 걸릴 수 있다.
	 */
	@PostMapping
	public ApiResponse<PrescriptionImportStartResponse> start(LoginUser loginUser, HttpSession session,
		@Valid @RequestBody PrescriptionImportStartRequest request) {
		PrescriptionImportService.StartResult result = importService.start(loginUser.userId(), session.getId(),
			request.authApp(), request.userName(), request.identity(), request.phoneNumber());
		return ApiResponse.ok(new PrescriptionImportStartResponse(result.importId(), result.expiresInSeconds(),
			result.source(), ServerIdentity.hostname()));
	}

	/**
	 * 2단계 : 휴대폰에서 승인한 뒤 호출 → 최근 1년 조제 내역. (저장은 pill-api 의 /api/pill/prescriptions 가 담당)
	 */
	@PostMapping("/{importId}/complete")
	public ApiResponse<PrescriptionImportCompleteResponse> complete(LoginUser loginUser, HttpSession session,
		@PathVariable String importId,
		@Valid @RequestBody PrescriptionImportCompleteRequest request) {
		PrescriptionImportService.CompleteResult result = importService.complete(loginUser.userId(), session.getId(),
			importId, request.identity());
		return ApiResponse.ok(new PrescriptionImportCompleteResponse(result.source(), result.prescriptions(),
			ServerIdentity.hostname()));
	}
}
