package com.lexhive.lending.loan;

import com.lexhive.lending.common.error.DuplicateActiveLoanException;
import com.lexhive.lending.common.error.HasOverdueLoansException;
import com.lexhive.lending.common.error.LoanLimitExceededException;
import com.lexhive.lending.config.LoanPolicyProperties;
import java.time.Duration;
import java.time.Instant;

/**
 * The borrowing rules, as plain Java: no Spring, no database, so every rule and boundary is unit-testable.
 * Limits come from configuration ({@link LoanPolicyProperties}), never from the database.
 */
public class LendingPolicy {

    private final int maxActiveLoans;
    private final Duration loanDuration;

    public LendingPolicy(LoanPolicyProperties properties) {
        this.maxActiveLoans = properties.maxActiveLoans();
        this.loanDuration = properties.duration();
    }

    /**
     * Rejects the borrow if any rule is violated. Rules are checked in a fixed order (overdue, then limit, then
     * duplicate) so a given state always yields the same, most actionable error.
     */
    public void checkCanBorrow(long memberId, long bookId, BorrowerStanding standing) {
        if (standing.hasOverdueLoans()) {
            throw new HasOverdueLoansException(memberId);
        }
        if (standing.activeLoans() >= maxActiveLoans) {
            throw new LoanLimitExceededException(memberId, standing.activeLoans(), maxActiveLoans);
        }
        if (standing.alreadyHoldsBook()) {
            throw new DuplicateActiveLoanException(memberId, bookId);
        }
    }

    public Instant dueDateFor(Instant borrowedAt) {
        return borrowedAt.plus(loanDuration);
    }

    public int maxActiveLoans() {
        return maxActiveLoans;
    }

    public Duration loanDuration() {
        return loanDuration;
    }
}
