package com.alyak.userapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 내약통한눈에 회원 서버(alyak-user-api)의 시작점.
 *
 * <p>담당 기능
 * <ul>
 *   <li>이메일 회원가입 · 로그인 · 로그아웃 (로그인 세션을 "만드는" 유일한 서버)</li>
 *   <li>마이페이지 : 내 정보 조회, 닉네임 · 비밀번호 변경, 회원 탈퇴</li>
 *   <li>간편인증(CODEF)으로 심평원 처방(조제) 내역 불러오기</li>
 * </ul>
 *
 * <p>같은 이미지로 컨테이너 2대(alyak-user-api-1, alyak-user-api-2)를 띄우고,
 * 세션은 Redis(alyak-redis)에 저장해서 어느 컨테이너가 요청을 받아도 같은 로그인 상태를 유지한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan // @ConfigurationProperties 가 붙은 설정 클래스(CodefProperties 등)를 자동 등록
@EnableJpaAuditing           // BaseTimeEntity 의 created_at / updated_at 자동 기록 활성화
public class AlyakUserApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(AlyakUserApiApplication.class, args);
	}
}
