package com.alyak.userapi.member.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 마이페이지 기능 : 내 정보 조회, 닉네임 변경, 비밀번호 변경, 회원 탈퇴.
 */
@Service
@RequiredArgsConstructor
public class MyPageService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	/** 회원 번호로 회원 조회. 세션은 있는데 회원이 지워진 경우 404 */
	@Transactional(readOnly = true)
	public Member getMember(Long memberId) {
		return findMember(memberId);
	}

	/** 닉네임 변경. 변경 감지(dirty checking)로 트랜잭션이 끝날 때 UPDATE 된다 */
	@Transactional
	public Member changeNickname(Long memberId, String nickname) {
		Member member = findMember(memberId);
		member.changeNickname(nickname.trim());
		return member;
	}

	/** 비밀번호 변경. 현재 비밀번호가 맞아야 바꿀 수 있다 */
	@Transactional
	public void changePassword(Long memberId, String currentPassword, String newPassword) {
		Member member = findMember(memberId);
		verifyPassword(member, currentPassword);
		member.changePasswordHash(passwordEncoder.encode(newPassword));
	}

	/**
	 * 회원 탈퇴 : 회원 정보를 즉시 삭제한다.
	 * (내 약통·저장한 처방내역은 pill-api 의 데이터라서, 프론트가 먼저 pill-api 의 삭제 API 를 호출한다)
	 */
	@Transactional
	public void withdraw(Long memberId, String password) {
		Member member = findMember(memberId);
		verifyPassword(member, password);
		memberRepository.delete(member);
	}

	private Member findMember(Long memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
	}

	private void verifyPassword(Member member, String rawPassword) {
		if (!passwordEncoder.matches(rawPassword, member.getPasswordHash())) {
			throw new BusinessException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
		}
	}
}
