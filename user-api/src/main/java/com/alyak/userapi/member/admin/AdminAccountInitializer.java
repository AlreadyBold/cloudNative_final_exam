package com.alyak.userapi.member.admin;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 서버가 뜰 때 관리자 계정이 없으면 하나 만든다.
 *
 * <ul>
 *   <li>이메일·비밀번호는 환경변수(ADMIN_EMAIL, ADMIN_PASSWORD)로만 받는다. 없으면 만들지 않는다.</li>
 *   <li>이미 같은 이메일의 계정이 있으면 아무것도 하지 않는다 (재시작해도 비밀번호가 바뀌지 않음).</li>
 *   <li>user-api 2대가 동시에 떠서 둘 다 만들려고 해도, DB 의 이메일 UNIQUE 제약 때문에 1개만 생긴다.</li>
 *   <li>회원가입의 비밀번호 길이 규칙(8자 이상)은 적용하지 않는다. 시연용 관리자 비밀번호를 env 에서 정하기 위함.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
	private static final String ADMIN_NICKNAME = "관리자";

	private final AdminProperties properties;
	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	@Override
	public void run(ApplicationArguments args) {
		if (!properties.isConfigured()) {
			log.warn("ADMIN_EMAIL / ADMIN_PASSWORD 가 없어서 관리자 계정을 만들지 않습니다.");
			return;
		}
		String email = properties.email().trim().toLowerCase(Locale.ROOT);
		if (memberRepository.existsByEmail(email)) {
			return;
		}
		try {
			memberRepository.saveAndFlush(
				Member.admin(email, passwordEncoder.encode(properties.password()), ADMIN_NICKNAME));
			log.info("관리자 계정을 만들었습니다.");
		} catch (DataIntegrityViolationException exception) {
			// 다른 user-api 컨테이너가 먼저 만들었다
			log.info("관리자 계정이 이미 있습니다.");
		}
	}
}
