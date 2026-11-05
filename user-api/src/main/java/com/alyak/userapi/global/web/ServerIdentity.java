package com.alyak.userapi.global.web;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * "이 요청을 어느 컨테이너가 처리했는지"를 알려 주는 값(served_by)을 만든다.
 *
 * <p>docker run 의 --hostname 옵션으로 컨테이너마다 hostname 을
 * alyak-user-api-1, alyak-user-api-2 처럼 다르게 지정한다.
 * 도커는 이 값을 컨테이너 안의 HOSTNAME 환경변수로도 넣어 주므로 그 값을 그대로 쓴다.
 */
public final class ServerIdentity {

	/** 서버가 뜰 때 한 번만 계산해 두고 재사용 */
	private static final String HOSTNAME = resolveHostname();

	private ServerIdentity() {
	}

	/** 이 컨테이너의 hostname (예: alyak-user-api-1) */
	public static String hostname() {
		return HOSTNAME;
	}

	/** HOSTNAME 환경변수 → 없으면 OS 에 hostname 을 물어본다 → 그래도 실패하면 unknown */
	private static String resolveHostname() {
		String fromEnv = System.getenv("HOSTNAME");
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException exception) {
			return "unknown";
		}
	}
}
