package com.alyak.userapi.member.dto;

import java.time.LocalDateTime;

import com.alyak.userapi.member.domain.Member;

/**
 * 회원 정보 응답 (비밀번호 해시는 절대 내보내지 않는다).
 *
 * @param id        회원 번호
 * @param email     이메일
 * @param nickname  닉네임
 * @param role      권한 (USER / ADMIN)
 * @param createdAt 가입 시각
 */
public record MemberResponse(Long id, String email, String nickname, String role, LocalDateTime createdAt) {

	/** 엔티티 → 응답 객체 변환 */
	public static MemberResponse from(Member member) {
		return new MemberResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole().name(),
			member.getCreatedAt());
	}
}
