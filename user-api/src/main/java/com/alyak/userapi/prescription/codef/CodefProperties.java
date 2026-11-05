package com.alyak.userapi.prescription.codef;

import org.springframework.boot.context.properties.ConfigurationProperties;

import io.codef.api.EasyCodefServiceType;

/**
 * application.properties 의 codef.* 설정. 값은 docker run 의 -e / --env-file 로 들어온다 (CODEF_*).
 *
 * @param serviceType  DEMO(데모 키, 실데이터) / API(정식 키) / SANDBOX(라이브러리 내장 공용 키, 고정 응답)
 * @param clientId     CODEF 에서 발급받은 클라이언트 ID
 * @param clientSecret CODEF 에서 발급받은 클라이언트 시크릿
 * @param publicKey    요청값 RSA 암호화용 공개키 (모든 환경에서 필요)
 */
@ConfigurationProperties(prefix = "codef")
public record CodefProperties(EasyCodefServiceType serviceType, String clientId, String clientSecret,
	String publicKey) {

	/**
	 * 실제 CODEF 를 호출할 수 있을 만큼 키가 채워져 있는지.
	 * false 면 PrescriptionImportService 가 모의(MOCK) 모드로 동작한다.
	 */
	public boolean isConfigured() {
		if (isBlank(publicKey)) {
			return false;
		}
		return serviceType == EasyCodefServiceType.SANDBOX || !isBlank(clientId) && !isBlank(clientSecret);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
