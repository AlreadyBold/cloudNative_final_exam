package com.alyak.userapi.member.domain;

/**
 * 회원 권한.
 *
 * <p>세션에 문자열("USER" / "ADMIN")로 저장되어 user-api 2대와 pill-api 가 함께 읽는다.
 */
public enum MemberRole {
	/** 일반 사용자 : 내 약통, 약 검색, 처방내역 불러오기 */
	USER,
	/** 관리자 : 백오피스 (대시보드, 회원 관리, 세션 확인). 사용자의 약 정보는 볼 수 없다 */
	ADMIN
}
