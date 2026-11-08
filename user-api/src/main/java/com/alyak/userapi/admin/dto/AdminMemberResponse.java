package com.alyak.userapi.admin.dto;

import java.time.LocalDateTime;

import com.alyak.userapi.member.domain.Member;

/**
 * 관리자 회원 목록의 한 줄. 비밀번호 해시와 사용자의 약 정보는 담지 않는다.
 *
 * @param id        회원 번호
 * @param email     이메일
 * @param nickname  닉네임
 * @param role      권한 (USER / ADMIN)
 * @param createdAt 가입 시각
 * @param online    지금 로그인돼 있는지 (Redis 에 살아 있는 로그인 세션이 있는지)
 */
public record AdminMemberResponse(Long id, String email, String nickname, String role, LocalDateTime createdAt,
	boolean online) {

	public static AdminMemberResponse of(Member member, boolean online) {
		return new AdminMemberResponse(member.getId(), member.getEmail(), member.getNickname(), member.getRole().name(),
			member.getCreatedAt(), online);
	}
}
