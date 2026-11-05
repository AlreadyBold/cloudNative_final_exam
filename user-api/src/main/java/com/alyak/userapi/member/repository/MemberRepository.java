package com.alyak.userapi.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alyak.userapi.member.domain.Member;

/**
 * 회원 테이블 조회/저장. Spring Data JPA 가 메서드 이름을 보고 SQL 을 자동으로 만든다.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

	/** 이메일로 회원 찾기 (로그인) → SELECT ... WHERE email = ? */
	Optional<Member> findByEmail(String email);

	/** 이메일 중복 확인 (회원가입) → SELECT ... WHERE email = ? 존재 여부 */
	boolean existsByEmail(String email);
}
