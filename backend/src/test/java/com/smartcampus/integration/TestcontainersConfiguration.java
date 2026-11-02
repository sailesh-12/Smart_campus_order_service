package com.smartcampus.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Throwaway Postgres and RabbitMQ for integration tests (and for TestBackendApplication).
 * Image versions are pinned so test runs are reproducible. Redis is not here: in CI it is a
 * redis:7 service container on localhost:6379 (REDIS_HOST / REDIS_PORT), locally any Redis on that port.
 * Tests never talk to the hosted Neon / Upstash / CloudAMQP services.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16.10-alpine"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13.7-management-alpine"));
	}
}
