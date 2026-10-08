package com.lexhive.lending.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL 16 for tests (same engine as production: partial indexes, CHECK constraints, row locks).
 * {@code @ServiceConnection} wires the datasource; Spring's context cache shares one container per context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerSupport {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
