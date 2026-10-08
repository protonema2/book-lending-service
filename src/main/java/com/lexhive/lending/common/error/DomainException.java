package com.lexhive.lending.common.error;

import org.springframework.http.HttpStatus;

/**
 * Root of all business errors. The hierarchy is sealed: every subtype is known and declares its own
 * HTTP status and machine-readable code, so {@link GlobalExceptionHandler} maps them with a single
 * handler instead of a type switch.
 */
public abstract sealed class DomainException extends RuntimeException
        permits ResourceNotFoundException, ResourceConflictException, LendingException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract HttpStatus status();

    /** Stable, machine-readable error code, e.g. {@code LOAN_LIMIT_EXCEEDED}. */
    public abstract String code();
}
