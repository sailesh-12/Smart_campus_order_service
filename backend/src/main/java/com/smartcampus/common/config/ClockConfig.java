package com.smartcampus.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Clock bean provider so all time operations can be controlled in tests.
 * Instants are zone-independent; the zone (app.time-zone, default Asia/Kolkata) only decides
 * the campus "day" (pickup codes are unique per vendor per day) and 5-minute slot boundaries.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.time-zone:Asia/Kolkata}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
