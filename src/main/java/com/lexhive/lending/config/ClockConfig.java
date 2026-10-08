package com.lexhive.lending.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Single source of "now" so time-dependent rules can be tested with a fixed or mutable clock. */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** Ticks in microseconds, PostgreSQL's timestamp precision, so returned instants equal what is stored. */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000));
    }
}
