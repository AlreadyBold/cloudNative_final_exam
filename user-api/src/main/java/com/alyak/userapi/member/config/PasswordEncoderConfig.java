package com.alyak.userapi.member.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시 도구 등록.
 *
 * <p>BCrypt 는 같은 비밀번호라도 매번 다른 해시가 나오고(솔트 포함), 일부러 느리게 계산해서
 * DB 가 유출돼도 원래 비밀번호를 알아내기 어렵게 한다.
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
