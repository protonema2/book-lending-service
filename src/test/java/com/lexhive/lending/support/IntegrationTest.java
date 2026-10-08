package com.lexhive.lending.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Full application against Testcontainers PostgreSQL with a {@link MutableClock}. Every test class using this
 * annotation shares one Spring context (and one container).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgresContainerSupport.class, IntegrationTest.ClockOverride.class})
public @interface IntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    class ClockOverride {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now(Clock.systemUTC()));
        }
    }
}
