package com.lexhive.lending.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Borrowing rules, bound from {@code library.loan.*}. Invalid values fail application startup.
 *
 * @param maxActiveLoans maximum number of unreturned loans a member may hold
 * @param duration       loan period; due date = borrow time + duration
 */
@Validated
@ConfigurationProperties(prefix = "library.loan")
public record LoanPolicyProperties(
        @Min(1) int maxActiveLoans,
        @NotNull Duration duration) {

    public LoanPolicyProperties {
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("library.loan.duration must be positive, was " + duration);
        }
    }
}
