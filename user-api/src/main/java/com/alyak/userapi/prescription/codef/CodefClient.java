package com.alyak.userapi.prescription.codef;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.codef.api.EasyCodef;
import io.codef.api.EasyCodefServiceType;

/**
 * CODEF API 호출 담당. 토큰 발급·재사용과 요청값 암호화는 공식 라이브러리(EasyCodef)가 처리한다.
 * 키가 설정되지 않았으면 isConfigured() 가 false 가 되어 모의(MOCK) 모드로 동작한다.
 *
 * <h3>간편인증 상품은 두 번 호출한다</h3>
 * <ol>
 *   <li>request : 처음 요청 → CODEF 가 심평원·인증기관(카카오 등)을 거쳐 사용자 휴대폰으로 인증 요청을 보내고,
 *       CF-03002(추가인증 필요) + twoWayInfo 를 돌려준다</li>
 *   <li>requestCertification : 사용자가 휴대폰에서 승인한 뒤, 같은 상품 URL 에
 *       처음 요청값 + is2Way / twoWayInfo / simpleAuth 를 붙여 다시 요청 → 결과 데이터</li>
 * </ol>
 *
 * 요청값에는 이름, 주민등록번호, 휴대폰 번호 같은 개인정보가 들어 있으므로 절대 로그에 남기지 않는다.
 */
@Component
public class CodefClient {

	private static final Logger log = LoggerFactory.getLogger(CodefClient.class);

	// 사용자 잘못이 아니라 "우리 쪽 설정/요청 형식" 문제일 때 CODEF 가 주는 코드
	// 키 오류(CF-00401 등), 공개키 없음(CF-00015), 2way 형식 오류(CF-03003/03004), 통신·서버 오류(CF-099xx)
	private static final Set<String> SYSTEM_ERROR_PREFIXES = Set.of("CF-000", "CF-004", "CF-099");
	private static final Set<String> SYSTEM_ERROR_CODES = Set.of("CF-03003", "CF-03004");

	private final CodefProperties properties;
	private final ObjectMapper objectMapper;
	private final EasyCodef codef = new EasyCodef();

	public CodefClient(CodefProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		configureTimeouts();
		if (!properties.isConfigured()) {
			// 키가 없어도 서버는 정상 기동한다. 처방내역 불러오기는 모의(MOCK) 데이터로 동작한다.
			log.warn("CODEF 키(CODEF_*)가 설정되지 않았습니다. 처방내역 불러오기는 모의(MOCK) 모드로 동작합니다.");
			return;
		}
		if (properties.serviceType() == EasyCodefServiceType.DEMO) {
			codef.setClientInfoForDemo(properties.clientId(), properties.clientSecret());
		} else if (properties.serviceType() == EasyCodefServiceType.API) {
			codef.setClientInfo(properties.clientId(), properties.clientSecret());
		}
		codef.setPublicKey(properties.publicKey());
		log.info("CODEF 연동 모드: {}", properties.serviceType());
	}

	/** 실제 CODEF 호출이 가능한 상태인지 (false 면 모의 모드) */
	public boolean isConfigured() {
		return properties.isConfigured();
	}

	/** 1차 요청 */
	public CodefResponse request(String productUrl, Map<String, Object> parameters) {
		return call(productUrl, parameters, false);
	}

	/** 2차 요청 (추가인증 완료 후) */
	public CodefResponse requestCertification(String productUrl, Map<String, Object> parameters) {
		return call(productUrl, parameters, true);
	}

	private CodefResponse call(String productUrl, Map<String, Object> parameters, boolean certification) {
		if (!properties.isConfigured()) {
			throw new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_UNAVAILABLE);
		}
		String body;
		try {
			HashMap<String, Object> parameterMap = new HashMap<>(parameters);
			body = certification
				? codef.requestCertification(productUrl, properties.serviceType(), parameterMap)
				: codef.requestProduct(productUrl, properties.serviceType(), parameterMap);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw unavailable(productUrl, exception);
		} catch (IOException exception) {
			throw unavailable(productUrl, exception);
		}
		CodefResponse response = parse(productUrl, body);
		// 우리 설정 문제는 사용자에게 CODEF 문구를 보여 주지 않고 503 + 서버 에러 로그로 처리
		if (!response.isSuccess() && isSystemError(response.code())) {
			log.error("CODEF 설정/요청 오류: {} code={} message={}", productUrl, response.code(), response.message());
			throw new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_UNAVAILABLE);
		}
		return response;
	}

	static boolean isSystemError(String code) {
		return SYSTEM_ERROR_CODES.contains(code) || SYSTEM_ERROR_PREFIXES.stream().anyMatch(code::startsWith);
	}

	/** CODEF 응답 문자열(JSON) → CodefResponse */
	private CodefResponse parse(String productUrl, String body) {
		try {
			JsonNode root = objectMapper.readTree(body);
			JsonNode result = root.path("result");
			return new CodefResponse(result.path("code").asText(), result.path("message").asText(), root.path("data"));
		} catch (IOException exception) {
			throw unavailable(productUrl, exception);
		}
	}

	/**
	 * EasyCodef 는 타임아웃 없이 HTTP 연결을 쓰므로, CODEF 가 응답하지 않으면 요청 스레드가 무한정 묶인다.
	 * JVM 기본 타임아웃을 건다 (연결 10초, 응답 310초 = CODEF 상품 timeout 300초 + 여유).
	 */
	private static void configureTimeouts() {
		if (System.getProperty("sun.net.client.defaultConnectTimeout") == null) {
			System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
		}
		if (System.getProperty("sun.net.client.defaultReadTimeout") == null) {
			System.setProperty("sun.net.client.defaultReadTimeout", "310000");
		}
	}

	private static BusinessException unavailable(String productUrl, Exception cause) {
		log.error("CODEF 호출 실패: {}", productUrl, cause);
		return new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_UNAVAILABLE);
	}
}
