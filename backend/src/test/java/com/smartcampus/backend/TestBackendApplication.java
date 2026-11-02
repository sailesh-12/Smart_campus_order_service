package com.smartcampus.backend;

import com.smartcampus.integration.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs the app locally on throwaway containers (Postgres, RabbitMQ and Redis) instead of hosted services. */
public class TestBackendApplication {

	@TestConfiguration(proxyBeanMethods = false)
	static class LocalRedis {
		@Bean
		@ServiceConnection(name = "redis")
		GenericContainer<?> redisContainer() {
			return new GenericContainer<>(DockerImageName.parse("redis:7.4.6-alpine")).withExposedPorts(6379);
		}
	}

	public static void main(String[] args) {
		SpringApplication.from(BackendApplication::main)
				.with(TestcontainersConfiguration.class, LocalRedis.class)
				.run(args);
	}
}
