package com.alyak.userapi.member.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.alyak.userapi.global.response.ApiResponse;
import com.alyak.userapi.global.session.LoginSessionRegistry;
import com.alyak.userapi.global.session.LoginUser;
import com.alyak.userapi.global.session.SessionAttributeNames;
import com.alyak.userapi.global.web.ServerIdentity;
import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.dto.LoginRequest;
import com.alyak.userapi.member.dto.MemberResponse;
import com.alyak.userapi.member.dto.SignupRequest;
import com.alyak.userapi.member.service.MemberAuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회원가입 · 로그인 · 로그아웃 API. 모두 로그인 없이 호출할 수 있다.
 *
 * <p>로그인에 성공하면 세션에 userId / username / nickname 을 저장한다.
 * Spring Session 이 이 세션을 Redis 에 저장하고, 응답에 SESSION 쿠키를 내려 준다.
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class MemberAuthController {

	private final MemberAuthService memberAuthService;
	private final LoginSessionRegistry loginSessionRegistry;

	/**
	 * 회원가입. 가입만 하고 로그인은 하지 않는다. (프론트가 가입 후 로그인 화면으로 이동)
	 * 성공하면 201 Created.
	 */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
		Member member = memberAuthService.signup(request.email(), request.password(), request.nickname());
		return ApiResponse.ok(MemberResponse.from(member));
	}

	/**
	 * 로그인 : 비밀번호 확인 → 세션 ID 새로 발급 → 세션에 로그인 정보 저장.
	 */
	@PostMapping("/login")
	public ApiResponse<LoginUser> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
		Member member = memberAuthService.authenticate(request.email(), request.password());

		// 세션이 없으면 새로 만들고(getSession(true)), 있으면 기존 세션을 가져온다
		HttpSession session = httpRequest.getSession(true);
		// 세션 고정 공격 방지 : 로그인 전에 쓰던 세션 ID 를 로그인 후에도 쓰지 않도록 ID 를 새로 바꾼다
		// (Spring Session 이 Redis 키 이름도 새 ID 로 바꿔 준다)
		httpRequest.changeSessionId();

		// ★ 세션에는 단순 값만 저장 (파이썬 서버가 JSON 으로 읽기 쉽게)
		session.setAttribute(SessionAttributeNames.USER_ID, member.getId());
		session.setAttribute(SessionAttributeNames.USERNAME, member.getEmail());
		session.setAttribute(SessionAttributeNames.NICKNAME, member.getNickname());
		session.setAttribute(SessionAttributeNames.ROLE, member.getRole().name());

		// 중복 로그인 방지 : 이 계정으로 다른 기기에서 로그인돼 있던 세션을 끊는다 (나중 로그인 우선)
		loginSessionRegistry.register(member.getId(), session.getId());

		return ApiResponse.ok(
			new LoginUser(member.getId(), member.getEmail(), member.getNickname(), member.getRole().name()));
	}

	/**
	 * 로그아웃 : 세션을 무효화하고 계정의 로그인 기록도 지운다. Spring Session 이 Redis 의 세션 키를 삭제하므로
	 * user-api 2대와 pill-api 모두에서 동시에 로그아웃된다.
	 * 이미 로그아웃된 상태에서 호출해도 성공으로 응답한다.
	 */
	@PostMapping("/logout")
	public ApiResponse<Void> logout(HttpServletRequest httpRequest) {
		HttpSession session = httpRequest.getSession(false);
		if (session != null) {
			LoginUser loginUser = LoginUser.from(session);
			if (loginUser != null) {
				loginSessionRegistry.unregister(loginUser.userId(), session.getId());
			}
			session.invalidate();
		}
		return ApiResponse.ok();
	}

	/**
	 * 서버 상태 확인 (로그인 불필요). 프록시가 요청을 어느 컨테이너로 보냈는지 확인할 때 쓴다.
	 */
	@GetMapping("/health")
	public ApiResponse<Map<String, String>> health() {
		return ApiResponse.ok(Map.of(
			"status", "UP",
			"served_by", ServerIdentity.hostname(),
			"service", "user-api (Spring Boot)"));
	}
}
