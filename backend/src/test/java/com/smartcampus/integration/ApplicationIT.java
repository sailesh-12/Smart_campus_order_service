package com.smartcampus.integration;

import com.smartcampus.backend.BackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole application against throwaway Postgres + RabbitMQ (Testcontainers) and the Redis
 * service container, runs Flyway, and checks the paths Kubernetes and clients depend on.
 * Runs with the "test" profile (no seed data, no hosted services, no secrets).
 */
@SpringBootTest(classes = BackendApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationIT {

	private static final Pattern TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

	@LocalServerPort
	int port;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void readinessAndLivenessProbesAreUp() throws Exception {
		HttpResponse<String> readiness = get("/actuator/health/readiness", null);
		HttpResponse<String> liveness = get("/actuator/health/liveness", null);

		assertThat(readiness.statusCode()).isEqualTo(200);
		assertThat(readiness.body()).contains("\"UP\"");
		assertThat(liveness.statusCode()).isEqualTo(200);
	}

	@Test
	void publicCatalogueWorksWithAnEmptyDatabase() throws Exception {
		HttpResponse<String> vendors = get("/vendors", null);

		assertThat(vendors.statusCode()).isEqualTo(200);
		assertThat(vendors.body().trim()).isEqualTo("[]");
	}

	@Test
	void studentCanRegisterLogInAndReadTheirProfile() throws Exception {
		String email = "it-" + UUID.randomUUID() + "@student.edu";
		String register = """
				{"name":"IT Student","email":"%s","password":"Password123","role":"STUDENT"}""".formatted(email);

		HttpResponse<String> registered = post("/auth/register", register);
		assertThat(registered.statusCode()).isEqualTo(201);

		// Login goes through the Redis-backed rate limiter, so this also proves Redis is reachable
		HttpResponse<String> login = post("/auth/login", """
				{"email":"%s","password":"Password123"}""".formatted(email));
		assertThat(login.statusCode()).isEqualTo(200);

		HttpResponse<String> me = get("/users/me", token(login.body()));
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(me.body()).contains(email);
	}

	@Test
	void protectedEndpointsNeedAToken() throws Exception {
		assertThat(get("/orders/me", null).statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> get(String path, String bearer) throws IOException, InterruptedException {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
		if (bearer != null) {
			request.header("Authorization", "Bearer " + bearer);
		}
		return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static String token(String body) {
		Matcher m = TOKEN.matcher(body);
		assertThat(m.find()).as("token in %s", body).isTrue();
		return m.group(1);
	}
}
