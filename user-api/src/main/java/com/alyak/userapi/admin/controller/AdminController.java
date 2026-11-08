package com.alyak.userapi.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alyak.userapi.admin.dto.AdminMemberResponse;
import com.alyak.userapi.admin.dto.DashboardResponse;
import com.alyak.userapi.admin.service.AdminService;
import com.alyak.userapi.admin.service.DashboardService;
import com.alyak.userapi.global.response.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 백오피스 API. 관리자만 호출할 수 있다 (AdminCheckInterceptor, 아니면 403).
 * 관리자도 사용자의 약 정보(내 약통)는 볼 수 없다 : 약 정보는 pill-api 에만 있고, 여기에는 회원 정보만 있다.
 */
@RestController
@RequestMapping("/api/user/admin")
@RequiredArgsConstructor
public class AdminController {

	private final AdminService adminService;
	private final DashboardService dashboardService;

	/** 대시보드 : 컨테이너별 상태 + 회원·세션 통계 */
	@GetMapping("/dashboard")
	public ApiResponse<DashboardResponse> dashboard() {
		return ApiResponse.ok(dashboardService.dashboard());
	}

	/** 회원 목록 */
	@GetMapping("/members")
	public ApiResponse<List<AdminMemberResponse>> members() {
		return ApiResponse.ok(adminService.members());
	}

	/** 비밀번호 초기화 (test1234) + 그 회원 로그아웃 */
	@PostMapping("/members/{memberId}/reset-password")
	public ApiResponse<Void> resetPassword(@PathVariable Long memberId) {
		adminService.resetPassword(memberId);
		return ApiResponse.ok();
	}
}
