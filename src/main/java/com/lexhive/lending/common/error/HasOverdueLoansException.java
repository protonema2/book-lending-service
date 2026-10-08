package com.lexhive.lending.common.error;

public final class HasOverdueLoansException extends LendingException {

    public HasOverdueLoansException(long memberId) {
        super("Member %d has overdue loans and cannot borrow until they are returned.".formatted(memberId));
    }

    @Override
    public String code() {
        return "HAS_OVERDUE_LOANS";
    }
}
