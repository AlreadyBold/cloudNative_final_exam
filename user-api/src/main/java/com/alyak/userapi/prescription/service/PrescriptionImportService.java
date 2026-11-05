package com.alyak.userapi.prescription.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.alyak.userapi.global.exception.BusinessException;
import com.alyak.userapi.global.exception.ErrorCode;
import com.alyak.userapi.prescription.codef.CodefClient;
import com.alyak.userapi.prescription.codef.CodefResponse;
import com.alyak.userapi.prescription.domain.PrescriptionDataSource;
import com.alyak.userapi.prescription.domain.SimpleAuthApp;
import com.alyak.userapi.prescription.dto.ImportedPrescription;
import com.alyak.userapi.prescription.service.PendingImportStore.PendingImport;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * 심평원 "내가 먹는 약 한눈에" 처방(조제) 내역 불러오기.
 *
 * <h3>흐름</h3>
 * <ol>
 *   <li>start : CODEF 1차 요청 → 사용자 휴대폰 간편인증 앱으로 인증 요청이 감
 *       → CODEF 가 준 twoWayInfo 를 Redis 에 270초 보관 → importId 를 프론트에 돌려줌</li>
 *   <li>(사용자가 휴대폰에서 [인증] 승인)</li>
 *   <li>complete : Redis 에서 대기 정보를 꺼내 CODEF 2차 요청 → 최근 1년 조제 내역 반환 (저장은 하지 않음)</li>
 * </ol>
 *
 * <h3>서버 2대 구성과 재현성을 위한 처리</h3>
 * <ul>
 *   <li>대기 정보와 하루 횟수를 서버 메모리 → Redis 로 이동 (user-api 2대 구성 대응)</li>
 *   <li>CODEF 키가 없으면 실제 호출 대신 모의(MOCK) 모드 : 같은 2단계 흐름으로 샘플 데이터를 돌려준다</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class PrescriptionImportService {

	/** CODEF "내가 먹는 약 한눈에" 상품 주소 */
	static final String PRODUCT_URL = "/v1/kr/public/hw/hira-list/my-medicine";
	/** CODEF 문서의 추가인증 제한시간 */
	static final Duration TWO_WAY_VALIDITY = Duration.ofSeconds(270);

	private static final Logger log = LoggerFactory.getLogger(PrescriptionImportService.class);
	/** CF-13000 : 주민등록번호 오류, CF-12837 : 간편인증 실명 불일치 */
	private static final Set<String> IDENTITY_MISMATCH_CODES = Set.of("CF-13000", "CF-12837");

	private final CodefClient codefClient;
	private final PendingImportStore pendingImportStore;
	private final ImportDailyLimiter dailyLimiter;
	private final MockPrescriptionProvider mockPrescriptionProvider;

	/** 시작 결과 : 완료 API 에 쓸 ID, 남은 시간, 데이터 출처 */
	public record StartResult(String importId, long expiresInSeconds, PrescriptionDataSource source) {
	}

	/** 완료 결과 : 데이터 출처, 조제 내역 */
	public record CompleteResult(PrescriptionDataSource source, List<ImportedPrescription> prescriptions) {
	}

	/**
	 * 1단계 : 간편인증 요청 보내기.
	 */
	public StartResult start(Long userId, SimpleAuthApp authApp, String userName, String identity,
		String phoneNumber) {
		// 입력값 형식 검사는 모의 모드에서도 똑같이 한다 (화면 흐름을 실제와 같게)
		Map<String, Object> baseParameters = baseParameters(authApp, userName.trim(),
			PhoneNumbers.normalize(phoneNumber));
		String importId = RandomTokens.opaqueToken();

		// ----- 모의 모드 : CODEF 를 호출하지 않고 대기 정보만 만든다 -----
		if (!codefClient.isConfigured()) {
			pendingImportStore.save(importId,
				new PendingImport(userId, PrescriptionDataSource.MOCK, Map.of(), Map.of()), TWO_WAY_VALIDITY);
			return new StartResult(importId, TWO_WAY_VALIDITY.toSeconds(), PrescriptionDataSource.MOCK);
		}

		// ----- 실제 모드 -----
		dailyLimiter.increaseOrThrow(userId);
		CodefResponse response = codefClient.request(PRODUCT_URL, withIdentity(baseParameters, identity));
		// 간편인증은 항상 추가인증을 거친다. 바로 성공/실패가 오면 1차 요청값이 잘못된 것
		if (!response.requiresTwoWay()) {
			throw failure(response);
		}
		pendingImportStore.save(importId,
			new PendingImport(userId, PrescriptionDataSource.CODEF, baseParameters, twoWayInfo(response.data())),
			TWO_WAY_VALIDITY);
		return new StartResult(importId, TWO_WAY_VALIDITY.toSeconds(), PrescriptionDataSource.CODEF);
	}

	/**
	 * 2단계 : 사용자가 휴대폰에서 승인한 뒤 결과 받기.
	 */
	public CompleteResult complete(Long userId, String importId, String identity) {
		PendingImport pending = pendingImportStore.find(importId)
			// 남의 importId 도 "없음"과 똑같이 응답 (다른 회원의 대기 건이 있는지 알 수 없게)
			.filter(found -> found.userId().equals(userId))
			.orElseThrow(() -> new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_EXPIRED));

		// ----- 모의 모드 : 샘플 데이터 반환 -----
		if (pending.source() == PrescriptionDataSource.MOCK) {
			pendingImportStore.delete(importId);
			return new CompleteResult(PrescriptionDataSource.MOCK, mockPrescriptionProvider.samples());
		}

		// ----- 실제 모드 : 1차 요청값 + 추가인증 정보로 2차 요청 -----
		Map<String, Object> parameters = withIdentity(pending.baseParameters(), identity);
		parameters.put("simpleAuth", "1");
		parameters.put("is2Way", true);
		parameters.put("twoWayInfo", pending.twoWayInfo());
		CodefResponse response = codefClient.requestCertification(PRODUCT_URL, parameters);

		if (response.isSuccess()) {
			pendingImportStore.delete(importId);
			return new CompleteResult(PrescriptionDataSource.CODEF, toPrescriptions(response.data()));
		}
		// 아직 휴대폰에서 승인 전이면 대기 정보를 남겨 두어 다시 [인증 완료]를 누를 수 있게 한다
		if (response.requiresTwoWay()) {
			throw new BusinessException(ErrorCode.PRESCRIPTION_AUTH_NOT_APPROVED);
		}
		if (IDENTITY_MISMATCH_CODES.contains(response.code())) {
			pendingImportStore.delete(importId);
		}
		throw failure(response);
	}

	/** CODEF 1차 요청값 (주민등록번호 제외) */
	private static Map<String, Object> baseParameters(SimpleAuthApp authApp, String userName, String phoneNumber) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("organization", "0020");          // 기관코드 : 건강보험심사평가원
		parameters.put("loginType", "5");                // 5 = 간편인증
		parameters.put("loginTypeLevel", authApp.getCodefLevel()); // 어느 간편인증 앱인지
		parameters.put("userName", userName);
		parameters.put("phoneNo", phoneNumber);
		parameters.put("detailYN", "1");                 // 상세 포함 : ATC 코드, 성상(모양/색) 등
		parameters.put("reqChildYN", "0");               // 자녀 내역 제외
		return parameters;
	}

	/** 기본 요청값에 주민등록번호(하이픈 제거)를 붙인 새 Map */
	private static Map<String, Object> withIdentity(Map<String, Object> baseParameters, String identity) {
		Map<String, Object> parameters = new HashMap<>(baseParameters);
		parameters.put("identity", identity.replace("-", ""));
		return parameters;
	}

	/** CODEF 응답에서 2차 요청에 필요한 추가인증 식별 정보만 꺼낸다 */
	private static Map<String, Object> twoWayInfo(JsonNode data) {
		Map<String, Object> twoWayInfo = new HashMap<>();
		twoWayInfo.put("jobIndex", data.path("jobIndex").asInt());
		twoWayInfo.put("threadIndex", data.path("threadIndex").asInt());
		twoWayInfo.put("jti", data.path("jti").asText());
		twoWayInfo.put("twoWayTimestamp", data.path("twoWayTimestamp").asLong());
		return twoWayInfo;
	}

	/** CODEF 실패 응답 → 사용자에게 보여 줄 예외 */
	private static BusinessException failure(CodefResponse response) {
		if (IDENTITY_MISMATCH_CODES.contains(response.code())) {
			return new BusinessException(ErrorCode.PRESCRIPTION_IDENTITY_MISMATCH);
		}
		log.warn("CODEF 처방내역 조회 실패: code={} message={}", response.code(), response.message());
		String message = response.message().isBlank()
			? ErrorCode.PRESCRIPTION_IMPORT_FAILED.getMessage()
			: response.message();
		return new BusinessException(ErrorCode.PRESCRIPTION_IMPORT_FAILED, message);
	}

	/** 조제 내역이 1건이면 객체, 여러 건이면 배열로 온다 → 항상 목록으로 바꾼다 */
	static List<ImportedPrescription> toPrescriptions(JsonNode data) {
		List<ImportedPrescription> prescriptions = new ArrayList<>();
		if (data.isArray()) {
			data.forEach(item -> prescriptions.add(toPrescription(item)));
		} else if (data.isObject() && !data.isEmpty()) {
			prescriptions.add(toPrescription(data));
		}
		return prescriptions;
	}

	/** CODEF 조제 1건(JSON) → ImportedPrescription. 필드 이름은 CODEF 문서 기준 */
	private static ImportedPrescription toPrescription(JsonNode item) {
		List<ImportedPrescription.Drug> drugs = new ArrayList<>();
		item.path("resDrugList").forEach(drug -> drugs.add(new ImportedPrescription.Drug(
			text(drug, "resDrugCode"),
			text(drug, "resDrugName"),
			text(drug, "resIngredients"),
			text(drug, "resContent"),
			text(drug, "resOneDose"),
			text(drug, "resDailyDosesNumber"),
			text(drug, "resTotalDosingdays"),
			text(drug, "resPrescribeDrugEffect"),
			text(drug, "resATCCode"),
			text(drug, "resFormula"))));
		return new ImportedPrescription(date(text(item, "resManufactureDate")), text(item, "commBrandName"),
			text(item, "resPrescribeOrg"), drugs);
	}

	/** 빈 문자열은 null 로 */
	private static String text(JsonNode node, String field) {
		String value = node.path(field).asText("").trim();
		return value.isEmpty() ? null : value;
	}

	/** "20260930" → 2026-09-30 (형식이 이상하면 null) */
	private static LocalDate date(String yyyyMMdd) {
		if (yyyyMMdd == null) {
			return null;
		}
		try {
			return LocalDate.parse(yyyyMMdd, DateTimeFormatter.BASIC_ISO_DATE);
		} catch (DateTimeParseException exception) {
			return null;
		}
	}
}
