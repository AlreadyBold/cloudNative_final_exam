package com.alyak.userapi.prescription.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.alyak.userapi.prescription.dto.ImportedPrescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * CODEF 키가 없을 때(모의 모드) 돌려줄 샘플 처방내역.
 *
 * <p>데이터는 src/main/resources/mock/sample-prescriptions.json 에 있고, 서버가 뜰 때 한 번 읽어 둔다.
 * 약품코드는 약 카탈로그(alyak_pill.pill_catalog_code)에 실제로 있는 보험코드를 써서,
 * pill-api 가 낱알 사진과 모양을 붙여 보여 줄 수 있게 했다.
 *
 * <h3>조제일은 "오늘 - 5일"로 그때그때 계산한다</h3>
 * 파일에 날짜를 고정해 두면 시간이 지날수록 모든 약이 "복용 완료"가 되어 약통 상태와 병용금기 검사를 보여 줄 수 없다.
 * 언제 실행해도 같은 모습이 되도록, 약 5개의 투약일수를 3·5·7·10·30일로 두었다.
 * <pre>
 * 타이레놀 3일 · 지르텍 5일 → 복용 완료       스포라녹스 7일 · 무코스타 10일 · 리피토 30일 → 복용 중
 * 리피토 × 스포라녹스 → 병용금기 (근육병증, 횡문근융해증 위험 증가 가능)
 * </pre>
 */
@Component
public class MockPrescriptionProvider {

	private static final String SAMPLE_FILE = "mock/sample-prescriptions.json";
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
	/** 샘플 처방의 조제일 = 오늘보다 며칠 전 */
	private static final int DISPENSED_DAYS_AGO = 5;

	private final List<ImportedPrescription> samples;

	public MockPrescriptionProvider(ObjectMapper objectMapper) {
		try (InputStream input = new ClassPathResource(SAMPLE_FILE).getInputStream()) {
			// JSON 필드는 snake_case (애플리케이션 ObjectMapper 설정과 동일)
			this.samples = List.copyOf(objectMapper.readValue(input, new TypeReference<List<ImportedPrescription>>() {
			}));
		} catch (IOException exception) {
			throw new IllegalStateException("샘플 처방내역 파일을 읽지 못했습니다: " + SAMPLE_FILE, exception);
		}
	}

	/** 샘플 처방내역. 조제일을 오늘(한국 날짜) 기준 5일 전으로 채워서 돌려준다 */
	public List<ImportedPrescription> samples() {
		LocalDate dispensedDate = LocalDate.now(KOREA).minusDays(DISPENSED_DAYS_AGO);
		return samples.stream()
			.map(rx -> new ImportedPrescription(dispensedDate, rx.pharmacy(), rx.prescriber(), rx.drugs()))
			.toList();
	}
}
