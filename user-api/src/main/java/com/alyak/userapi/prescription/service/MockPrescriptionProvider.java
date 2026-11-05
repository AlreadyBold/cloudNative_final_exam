package com.alyak.userapi.prescription.service;

import java.io.IOException;
import java.io.InputStream;
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
 */
@Component
public class MockPrescriptionProvider {

	private static final String SAMPLE_FILE = "mock/sample-prescriptions.json";

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

	/** 샘플 처방내역 (변경 불가 목록) */
	public List<ImportedPrescription> samples() {
		return samples;
	}
}
