package com.alyak.userapi.prescription.codef;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CODEF 응답 한 건. 응답 JSON 형식 : {"result": {"code", "message", ...}, "data": {...}}
 *
 * @param code    CF-00000 성공, CF-03002 추가인증 필요(간편인증 승인 대기), 그 외 실패
 * @param message CODEF 가 준 안내 문구
 * @param data    상품별 응답. 추가인증 필요 시 jobIndex, threadIndex, jti, twoWayTimestamp 가 들어 있다
 */
public record CodefResponse(String code, String message, JsonNode data) {

	/** 성공 코드 */
	public static final String SUCCESS = "CF-00000";
	/** 추가인증(간편인증 승인) 필요 코드 */
	public static final String TWO_WAY_REQUIRED = "CF-03002";

	public boolean isSuccess() {
		return SUCCESS.equals(code);
	}

	/** 사용자가 휴대폰 앱에서 승인해야 다음 단계로 갈 수 있는 상태인지 */
	public boolean requiresTwoWay() {
		return TWO_WAY_REQUIRED.equals(code) && data.path("continue2Way").asBoolean(false);
	}
}
