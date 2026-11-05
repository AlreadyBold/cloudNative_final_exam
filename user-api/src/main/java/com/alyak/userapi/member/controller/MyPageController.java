package com.alyak.userapi.member.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alyak.userapi.global.response.ApiResponse;
import com.alyak.userapi.global.session.LoginUser;
import com.alyak.userapi.global.session.SessionAttributeNames;
import com.alyak.userapi.global.web.ServerIdentity;
import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.dto.ChangeNicknameRequest;
import com.alyak.userapi.member.dto.ChangePasswordRequest;
import com.alyak.userapi.member.dto.MemberResponse;
import com.alyak.userapi.member.dto.SessionCheckResponse;
import com.alyak.userapi.member.dto.WithdrawRequest;
import com.alyak.userapi.member.service.MyPageService;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 마이페이지 API. 모두 로그인 필요 (LoginCheckInterceptor 가 미로그인 요청을 401 로 막는다).
 */
@RestController
@RequestMapping("/api/user/me")
@RequiredArgsConstructor
public class MyPageController {

	/** 세션 ID 를 응답에 보여 줄 때 앞에서부터 몇 글자만 보여 줄지 */
	private static final int SESSION_ID_PREFIX_LENGTH = 8;

	private final MyPageService myPageService;

	/**
	 * 내 정보 + 세션 확인 정보.
	 * 프론트 상단 "세션 확인" 패널이 pill-api 의 /api/pill/me 와 함께 호출한다.
	 *
	 * @param loginUser 세션의 로그인 사용자 (LoginUserArgumentResolver 가 자동 주입)
	 * @param session   현재 세션 (Spring 이 자동 주입)
	 */
	@GetMapping
	public ApiResponse<SessionCheckResponse> me(LoginUser loginUser, HttpSession session) {
		Member member = myPageService.getMember(loginUser.userId());
		return ApiResponse.ok(new SessionCheckResponse(
			loginUser,
			MemberResponse.from(member),
			session.getId().substring(0, SESSION_ID_PREFIX_LENGTH),
			ServerIdentity.hostname(),
			"user-api (Spring Boot)"));
	}

	/**
	 * 닉네임 변경. DB 와 함께 "세션의 nickname" 도 바꿔서
	 * 다른 서버(user-api 다른 컨테이너, pill-api)에도 바로 새 닉네임이 보이게 한다.
	 */
	@PatchMapping
	public ApiResponse<MemberResponse> changeNickname(LoginUser loginUser, HttpSession session,
		@Valid @RequestBody ChangeNicknameRequest request) {
		Member member = myPageService.changeNickname(loginUser.userId(), request.nickname());
		session.setAttribute(SessionAttributeNames.NICKNAME, member.getNickname());
		return ApiResponse.ok(MemberResponse.from(member));
	}

	/** 비밀번호 변경 (현재 비밀번호 확인 후) */
	@PatchMapping("/password")
	public ApiResponse<Void> changePassword(LoginUser loginUser, @Valid @RequestBody ChangePasswordRequest request) {
		myPageService.changePassword(loginUser.userId(), request.currentPassword(), request.newPassword());
		return ApiResponse.ok();
	}

	/** 회원 탈퇴 : 회원 삭제 후 세션도 무효화(=로그아웃) */
	@DeleteMapping
	public ApiResponse<Void> withdraw(LoginUser loginUser, HttpSession session,
		@Valid @RequestBody WithdrawRequest request) {
		myPageService.withdraw(loginUser.userId(), request.password());
		session.invalidate();
		return ApiResponse.ok();
	}
}
