package com.lexhive.lending.common.error;

import org.springframework.http.HttpStatus;

/** A borrow/return request rejected by a lending rule. Rejections are counted per {@link #code()}. */
public abstract sealed class LendingException extends DomainException
        permits BookUnavailableException, LoanLimitExceededException, HasOverdueLoansException,
                DuplicateActiveLoanException, LoanAlreadyReturnedException {

    protected LendingException(String message) {
        super(message);
    }

    @Override
    public HttpStatus status() {
        return HttpStatus.CONFLICT;
    }
}
