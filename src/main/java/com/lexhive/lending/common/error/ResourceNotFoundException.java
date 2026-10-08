package com.lexhive.lending.common.error;

import org.springframework.http.HttpStatus;

public final class ResourceNotFoundException extends DomainException {

    private final String code;

    private ResourceNotFoundException(String resource, String code, long id) {
        super("%s %d not found.".formatted(resource, id));
        this.code = code;
    }

    public static ResourceNotFoundException book(long id) {
        return new ResourceNotFoundException("Book", "BOOK_NOT_FOUND", id);
    }

    public static ResourceNotFoundException member(long id) {
        return new ResourceNotFoundException("Member", "MEMBER_NOT_FOUND", id);
    }

    public static ResourceNotFoundException loan(long id) {
        return new ResourceNotFoundException("Loan", "LOAN_NOT_FOUND", id);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.NOT_FOUND;
    }

    @Override
    public String code() {
        return code;
    }
}
