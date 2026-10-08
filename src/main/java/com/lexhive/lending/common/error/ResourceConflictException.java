package com.lexhive.lending.common.error;

import org.springframework.http.HttpStatus;

/** Catalog/member management conflicts (uniqueness, deletion and stock guards). */
public final class ResourceConflictException extends DomainException {

    private final String code;

    private ResourceConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static ResourceConflictException duplicateIsbn(String isbn) {
        return new ResourceConflictException("DUPLICATE_ISBN", "A book with ISBN %s already exists.".formatted(isbn));
    }

    public static ResourceConflictException duplicateEmail(String email) {
        return new ResourceConflictException("DUPLICATE_EMAIL", "A member with email %s already exists.".formatted(email));
    }

    public static ResourceConflictException copiesOnLoan(long bookId, int onLoan, int requestedTotal) {
        return new ResourceConflictException("COPIES_ON_LOAN",
                "Book %d has %d copies on loan; total copies cannot be set to %d.".formatted(bookId, onLoan, requestedTotal));
    }

    public static ResourceConflictException activeLoansExist(String resource, long id) {
        return new ResourceConflictException("ACTIVE_LOANS_EXIST",
                "%s %d has active loans and cannot be deleted.".formatted(resource, id));
    }

    public static ResourceConflictException loanHistoryExists(String resource, long id) {
        return new ResourceConflictException("LOAN_HISTORY_EXISTS",
                "%s %d has loan history and cannot be deleted.".formatted(resource, id));
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }

    @Override
    public String code() {
        return code;
    }
}
