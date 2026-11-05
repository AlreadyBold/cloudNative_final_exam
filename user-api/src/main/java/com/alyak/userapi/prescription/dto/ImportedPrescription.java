package com.alyak.userapi.prescription.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 심평원에서 불러온 조제 1건 (최근 1년).
 *
 * @param dispensedDate 조제일 (예: 2026-09-30)
 * @param pharmacy      조제기관(약국)
 * @param prescriber    처방기관(병원)
 * @param drugs         이 조제 건에 들어 있는 약 목록
 */
public record ImportedPrescription(
	LocalDate dispensedDate,
	String pharmacy,
	String prescriber,
	List<Drug> drugs
) {

	/**
	 * 조제 건 안의 약 1개.
	 *
	 * @param drugCode        보험 약품코드 9자리 (pill-api 가 이 코드로 약 카탈로그를 찾아 낱알 사진을 붙인다)
	 * @param name            약품명
	 * @param ingredients     성분명
	 * @param content         함량
	 * @param oneDose         1회 투약량
	 * @param dailyDoses      1일 투여횟수
	 * @param totalDosingDays 총 투약일수
	 * @param effect          약효
	 * @param atcCode         WHO-ATC 코드
	 * @param formula         성상 (예: 흰색의 원형 정제)
	 */
	public record Drug(
		String drugCode,
		String name,
		String ingredients,
		String content,
		String oneDose,
		String dailyDoses,
		String totalDosingDays,
		String effect,
		String atcCode,
		String formula
	) {
	}
}
