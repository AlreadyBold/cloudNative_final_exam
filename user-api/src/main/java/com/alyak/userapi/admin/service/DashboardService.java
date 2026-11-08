package com.alyak.userapi.admin.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.alyak.userapi.admin.dto.DashboardResponse;
import com.alyak.userapi.admin.dto.DashboardResponse.ServiceStatus;
import com.alyak.userapi.admin.dto.DashboardResponse.Stats;
import com.alyak.userapi.global.session.LoginSessionRegistry;
import com.alyak.userapi.global.web.ServerIdentity;
import com.alyak.userapi.member.domain.Member;
import com.alyak.userapi.member.domain.MemberRole;
import com.alyak.userapi.member.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자 대시보드 : 컨테이너 8개의 상태와 회원·세션 통계.
 *
 * <p>user-api 는 도커 네트워크(alyak-net) 안에 있으므로 다른 컨테이너를 "이름"으로 직접 부를 수 있다.
 * 프록시를 거치지 않고 alyak-user-api-1, alyak-user-api-2 를 각각 따로 확인해야 2대 중 어느 쪽이 죽었는지 알 수 있다.
 * 여러 컨테이너를 동시에(병렬로) 확인해서, 하나가 응답하지 않아도 전체가 최대 2초 안에 끝난다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

	private static final Duration TIMEOUT = Duration.ofSeconds(2);
	private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

	/** HTTP 로 확인할 컨테이너 : 이름, 역할, 확인할 주소 (컨테이너 이름 = 도커 내장 DNS 이름) */
	private static final List<String[]> HTTP_TARGETS = List.of(
		new String[] {"alyak-proxy", "역방향 프록시 (nginx)", "http://alyak-proxy/"},
		new String[] {"alyak-front", "프론트 (nginx, HTML/JS)", "http://alyak-front/"},
		new String[] {"alyak-user-api-1", "회원 서버 1 (Spring Boot)", "http://alyak-user-api-1:8080/api/user/health"},
		new String[] {"alyak-user-api-2", "회원 서버 2 (Spring Boot)", "http://alyak-user-api-2:8080/api/user/health"},
		new String[] {"alyak-pill-api", "약 서버 (FastAPI)", "http://alyak-pill-api:8000/api/pill/health"});

	private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
	private final StringRedisTemplate redisTemplate;
	private final JdbcTemplate jdbcTemplate;
	private final MemberRepository memberRepository;
	private final LoginSessionRegistry loginSessionRegistry;

	public DashboardResponse dashboard() {
		// 1) 컨테이너 상태 : HTTP 5개 + Redis + MySQL 을 동시에 확인
		List<CompletableFuture<ServiceStatus>> checks = new ArrayList<>();
		for (String[] target : HTTP_TARGETS) {
			checks.add(CompletableFuture.supplyAsync(() -> checkHttp(target[0], target[1], target[2])));
		}
		checks.add(CompletableFuture.supplyAsync(() -> check("alyak-redis", "세션 저장소 (Redis)",
			() -> "PONG".equals(redisTemplate.execute((RedisCallback<String>) connection -> connection.ping()))
				? "PONG" : null)));
		checks.add(CompletableFuture.supplyAsync(() -> check("alyak-mysql", "DB (MySQL 8.4)",
			() -> "MySQL " + jdbcTemplate.queryForObject("SELECT VERSION()", String.class))));
		List<ServiceStatus> services = checks.stream().map(CompletableFuture::join).toList();

		// 2) 통계 (사용자의 약 정보는 보지 않는다)
		List<Member> users = memberRepository.findAll().stream().filter(m -> m.getRole() == MemberRole.USER).toList();
		LocalDateTime todayStart = LocalDate.now(KOREA).atStartOfDay();
		long online = users.stream().filter(m -> loginSessionRegistry.currentSessionId(m.getId()) != null).count();
		long newToday = users.stream().filter(m -> !m.getCreatedAt().isBefore(todayStart)).count();
		Stats stats = new Stats(users.size(), online, newToday, countSessions());

		return new DashboardResponse(services, stats, ServerIdentity.hostname(), LocalDateTime.now(KOREA));
	}

	/** HTTP 로 확인 : 2xx 면 UP. 응답 헤더 X-Served-By(처리한 서버 이름)를 함께 보여 준다 */
	private ServiceStatus checkHttp(String name, String role, String url) {
		return check(name, role, () -> {
			HttpResponse<Void> response = httpClient.send(
				HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build(),
				HttpResponse.BodyHandlers.discarding());
			if (response.statusCode() / 100 != 2) {
				throw new IllegalStateException("HTTP " + response.statusCode());
			}
			return response.headers().firstValue("X-Served-By").orElse("HTTP " + response.statusCode());
		});
	}

	/** 확인 작업을 실행하고 걸린 시간을 잰다. 예외가 나면 DOWN */
	private ServiceStatus check(String name, String role, CheckedSupplier<String> task) {
		long start = System.nanoTime();
		try {
			String detail = task.get();
			long elapsed = (System.nanoTime() - start) / 1_000_000;
			return new ServiceStatus(name, role, detail != null, elapsed, detail);
		} catch (Exception exception) {
			return new ServiceStatus(name, role, false, null, exception.getClass().getSimpleName());
		}
	}

	/** Redis 의 세션 키 개수 (KEYS 대신 SCAN 으로 조금씩 세어서 Redis 를 막지 않는다) */
	private long countSessions() {
		long count = 0;
		ScanOptions options = ScanOptions.scanOptions().match("alyak:session:sessions:*").count(500).build();
		try (Cursor<String> cursor = redisTemplate.scan(options)) {
			while (cursor.hasNext()) {
				cursor.next();
				count++;
			}
		}
		return count;
	}

	/** 예외를 던질 수 있는 Supplier */
	@FunctionalInterface
	private interface CheckedSupplier<T> {
		T get() throws Exception;
	}
}
