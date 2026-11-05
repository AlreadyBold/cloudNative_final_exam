package com.alyak.userapi.member.dto;

import com.alyak.userapi.global.session.LoginUser;

/**
 * GET /api/user/me 응답 : 내 정보 + 세션 공유 검증용 정보.
 *
 * <p>파이썬 서버의 GET /api/pill/me 도 같은 필드(login_user, session_id_prefix, served_by, service)를 돌려준다.
 * 프론트의 "세션 확인" 패널이 두 응답을 나란히 보여 주면
 * "같은 사용자 · 같은 세션 · 다른 서버" 임을 한눈에 확인할 수 있다.
 *
 * @param loginUser       세션에 저장된 로그인 사용자 (userId, username, nickname)
 * @param member          DB 에서 읽은 회원 정보 (가입일 등)
 * @param sessionIdPrefix 세션 ID 앞 8자리 (전체를 노출하지 않기 위해 앞부분만)
 * @param servedBy        이 요청을 처리한 컨테이너 hostname
 * @param service         서버 종류 설명
 */
public record SessionCheckResponse(
	LoginUser loginUser,
	MemberResponse member,
	String sessionIdPrefix,
	String servedBy,
	String service
) {
}
