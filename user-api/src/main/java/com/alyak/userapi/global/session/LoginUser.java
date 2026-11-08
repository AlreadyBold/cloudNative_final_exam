package com.alyak.userapi.global.session;

import jakarta.servlet.http.HttpSession;

/**
 * 현재 요청을 보낸 로그인 사용자 (세션에서 꺼낸 값).
 *
 * <p>컨트롤러 메서드 파라미터에 {@code LoginUser loginUser} 를 적으면
 * LoginUserArgumentResolver 가 세션에서 값을 꺼내 자동으로 넣어 준다.
 *
 * @param userId   회원 번호
 * @param username 로그인 아이디(이메일)
 * @param nickname 화면 표시 이름
 * @param role     권한 (USER / ADMIN)
 */
public record LoginUser(Long userId, String username, String nickname, String role) {

	/** 관리자인지 */
	public boolean isAdmin() {
		return "ADMIN".equals(role);
	}

	/**
	 * 세션에서 로그인 사용자 정보를 꺼낸다. 로그인하지 않은 세션이면 null.
	 *
	 * <p>userId 는 Redis(JSON)에서 다시 읽을 때 Integer 나 Long 으로 돌아올 수 있으므로
	 * Number 로 받아 long 으로 바꾼다.
	 */
	public static LoginUser from(HttpSession session) {
		if (session == null) {
			return null;
		}
		Object userId = session.getAttribute(SessionAttributeNames.USER_ID);
		if (!(userId instanceof Number number)) {
			return null;
		}
		return new LoginUser(
			number.longValue(),
			(String) session.getAttribute(SessionAttributeNames.USERNAME),
			(String) session.getAttribute(SessionAttributeNames.NICKNAME),
			(String) session.getAttribute(SessionAttributeNames.ROLE));
	}
}
