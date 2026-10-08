package com.lexhive.lending.loan.dto;

import com.lexhive.lending.loan.Loan;
import com.lexhive.lending.loan.LoanStatus;
import java.time.Instant;

public record LoanResponse(
        long id,
        long bookId,
        long memberId,
        Instant borrowedAt,
        Instant dueDate,
        Instant returnedAt,
        LoanStatus status) {

    public static LoanResponse from(Loan loan, Instant now) {
        return new LoanResponse(loan.getId(), loan.getBookId(), loan.getMemberId(), loan.getBorrowedAt(),
                loan.getDueDate(), loan.getReturnedAt(), loan.statusAt(now));
    }
}
