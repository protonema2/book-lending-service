package com.lexhive.lending.common.error;

public final class LoanLimitExceededException extends LendingException {

    public LoanLimitExceededException(long memberId, long activeLoans, int maxActiveLoans) {
        super("Member %d already has %d active loans (max %d).".formatted(memberId, activeLoans, maxActiveLoans));
    }

    @Override
    public String code() {
        return "LOAN_LIMIT_EXCEEDED";
    }
}
