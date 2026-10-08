package com.lexhive.lending.common.error;

public final class LoanAlreadyReturnedException extends LendingException {

    public LoanAlreadyReturnedException(long loanId) {
        super("Loan %d has already been returned.".formatted(loanId));
    }

    @Override
    public String code() {
        return "LOAN_ALREADY_RETURNED";
    }
}
