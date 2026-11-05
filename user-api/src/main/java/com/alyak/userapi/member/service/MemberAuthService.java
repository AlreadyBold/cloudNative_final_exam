package com.alyak.userapi.member.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 회원가입과 로그인 검증 로직.
 *
 * <p>메일 계정 없이도 재현할 수 있도록 이메일 인증 없이 바로 가입한다.
 * 세션에 값을 넣는 일은 컨트롤러(MemberAuthController)가 한다.
 */
@Service
@RequiredArgsConstructor
public class MemberAuthService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	/**
	 * 회원가입. 이메일은 소문자로 맞춰 저장해서 대소문자만 다른 중복 가입을 막는다.
	 *
	 * @return 저장된 회원
	 */
	@Transactional
	public Member signup(String email, String password, String nickname) {
		String normalizedEmail = normalizeEmail(email);
		if (memberRepository.existsByEmail(normalizedEmail)) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
		Member member = new Member(normalizedEmail, passwordEncoder.encode(password), nickname.trim());
		try {
			// saveAndFlush : 바로 INSERT 해서, 거의 동시에 들어온 같은 이메일 가입을 DB 유니크 제약으로 잡아낸다
			return memberRepository.saveAndFlush(member);
		} catch (DataIntegrityViolationException exception) {
			throw new BusinessException(ErrorCode.AUTH_EMAIL_ALREADY_EXISTS);
		}
	}

	/**
	 * 로그인 검증. 이메일이 없을 때와 비밀번호가 틀릴 때 "같은 에러"를 돌려준다.
	 * (어떤 이메일이 가입돼 있는지 외부에서 알아내지 못하게 하기 위함)
	 *
	 * @return 비밀번호가 맞으면 해당 회원
	 */
	@Transactional(readOnly = true)
	public Member authenticate(String email, String password) {
		Member member = memberRepository.findByEmail(normalizeEmail(email))
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(password, member.getPasswordHash())) {
			throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
		}
		return member;
	}

	/** 앞뒤 공백 제거 + 소문자 */
	private static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
