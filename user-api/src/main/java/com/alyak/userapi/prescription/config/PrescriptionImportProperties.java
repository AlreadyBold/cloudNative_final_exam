package com.alyak.userapi.prescription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.properties 의 prescription-import.* 설정.
 *
 * @param maxPerDay 회원 1명이 하루에 실제 CODEF 조회를 시작할 수 있는 최대 횟수
 */
@ConfigurationProperties(prefix = "prescription-import")
public record PrescriptionImportProperties(int maxPerDay) {
}
