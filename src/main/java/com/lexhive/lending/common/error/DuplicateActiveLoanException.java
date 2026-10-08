package com.lexhive.lending.common.error;

public final class DuplicateActiveLoanException extends LendingException {

    public DuplicateActiveLoanException(long memberId, long bookId) {
        super("Member %d already has an active loan of book %d.".formatted(memberId, bookId));
    }

    @Override
    public String code() {
        return "DUPLICATE_ACTIVE_LOAN";
    }
}
