package com.alyak.userapi.global.session;

/**
 * 로그인 세션에 저장하는 속성 이름 모음.
 *
 * <p>파이썬 서버(alyak-pill-api)가 Redis 에서 "sessionAttr:" + 아래 이름으로 같은 값을 읽는다.
 * 그래서 이름을 바꾸면 pill-api 의 app/session.py 도 함께 바꿔야 한다.
 *
 * <p>세션에는 DTO 객체를 통째로 넣지 않고 숫자·문자열 같은 "단순 값"만 넣는다.
 * 객체를 JSON 으로 저장하면 자바 클래스 이름(@class)이 함께 들어가서 다른 언어가 해석하기 어렵기 때문이다.
 */
public final class SessionAttributeNames {

	/** 회원 번호 (숫자, users.id) */
	public static final String USER_ID = "userId";
	/** 로그인 아이디 (문자열, 이메일) */
	public static final String USERNAME = "username";
	/** 화면 표시 이름 (문자열) */
	public static final String NICKNAME = "nickname";

	// 상수만 모아 둔 클래스라 객체 생성을 막는다
	private SessionAttributeNames() {
	}
}
