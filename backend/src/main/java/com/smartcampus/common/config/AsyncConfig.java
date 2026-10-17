package com.smartcampus.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables asynchronous task execution for non-blocking operations like push notifications.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
