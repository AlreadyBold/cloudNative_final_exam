package com.alyak.userapi.admin.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 대시보드 응답.
 *
 * @param services  컨테이너별 상태
 * @param stats     회원·세션 통계
 * @param servedBy  이 응답을 만든 user-api 컨테이너
 * @param checkedAt 확인한 시각
 */
public record DashboardResponse(List<ServiceStatus> services, Stats stats, String servedBy, LocalDateTime checkedAt) {

	/**
	 * 컨테이너 1개의 상태.
	 *
	 * @param name       컨테이너 이름 (docker run --name 과 같다)
	 * @param role       역할 설명
	 * @param up         응답했는지
	 * @param responseMs 응답 시간(ms). 응답하지 않았으면 null
	 * @param detail     추가 정보 (응답한 서버 이름, 오류 이유 등)
	 */
	public record ServiceStatus(String name, String role, boolean up, Long responseMs, String detail) {
	}

	/**
	 * 통계 (사용자의 약 정보는 포함하지 않는다).
	 *
	 * @param totalMembers   전체 회원 수 (관리자 제외)
	 * @param onlineMembers  지금 로그인 중인 회원 수 (관리자 제외)
	 * @param newMembersToday 오늘 가입한 회원 수
	 * @param activeSessions Redis 에 저장된 세션 수 (로그인 전 세션 포함)
	 */
	public record Stats(long totalMembers, long onlineMembers, long newMembersToday, long activeSessions) {
	}
}
