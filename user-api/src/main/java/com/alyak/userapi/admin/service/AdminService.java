package com.alyak.userapi.admin.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alyak.userapi.admin.dto.AdminMemberResponse;
import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.global.session.LoginSessionRegistry;
import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 회원 관리 : 회원 목록, 비밀번호 초기화.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

	/** 비밀번호 초기화 시 바뀌는 비밀번호 (모든 회원 동일). 사용자는 로그인 후 마이페이지에서 바꾼다 */
	public static final String RESET_PASSWORD = "test1234";

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;
	private final LoginSessionRegistry loginSessionRegistry;

	/** 회원 목록 (가입 순) + 지금 로그인 중인지 */
	@Transactional(readOnly = true)
	public List<AdminMemberResponse> members() {
		return memberRepository.findAll(Sort.by("id")).stream()
			.map(member -> AdminMemberResponse.of(member,
				loginSessionRegistry.currentSessionId(member.getId()) != null))
			.toList();
	}

	/**
	 * 비밀번호를 test1234 로 초기화하고, 그 회원이 로그인 중이면 로그아웃시킨다 (옛 비밀번호로 쓰던 세션을 끊음).
	 * 관리자 계정은 초기화할 수 없다.
	 */
	@Transactional
	public void resetPassword(Long memberId) {
		Member member = memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
		if (member.isAdmin()) {
			throw new BusinessException(ErrorCode.ADMIN_RESET_NOT_ALLOWED);
		}
		member.changePasswordHash(passwordEncoder.encode(RESET_PASSWORD));
		loginSessionRegistry.forceLogout(memberId);
	}
}
