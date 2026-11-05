package com.alyak.userapi.member.domain;

import com.alyak.userapi.global.entity.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 엔티티 (alyak_user.users 테이블).
 *
 * <p>이메일 로그인만 지원하므로 계정과 로그인 정보를 하나의 테이블에 둔다.
 * 테이블은 db/initdb/01_create_schemas_accounts_tables.sql 이 만든다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 용 기본 생성자 (외부에서 빈 객체 생성 금지)
public class Member extends BaseTimeEntity {

	/** 닉네임 최대 길이 (DB 컬럼 VARCHAR(20) 과 동일) */
	public static final int NICKNAME_MAX_LENGTH = 20;

	/** 회원 번호 (AUTO_INCREMENT). 세션의 userId 로 저장된다 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 로그인 아이디(이메일). 중복 불가 */
	@Column(nullable = false, unique = true, length = 100)
	private String email;

	/** BCrypt 로 해시한 비밀번호. 원문은 어디에도 저장하지 않는다 */
	@Column(nullable = false, length = 100)
	private String passwordHash;

	/** 화면에 표시할 이름 */
	@Column(nullable = false, length = NICKNAME_MAX_LENGTH)
	private String nickname;

	/** 회원가입 시 사용하는 생성자 */
	public Member(String email, String passwordHash, String nickname) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
	}

	/** 닉네임 변경 (트랜잭션 안에서 호출하면 JPA 가 변경을 감지해 UPDATE 한다) */
	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	/** 비밀번호 변경 (이미 해시한 값을 받는다) */
	public void changePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}
}
