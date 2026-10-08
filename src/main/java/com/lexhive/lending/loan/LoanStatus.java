package com.lexhive.lending.loan;

/** Derived (not stored) state of a loan at a given instant. */
public enum LoanStatus {
    /** Not returned, due date not yet passed. */
    ACTIVE,
    /** Not returned, due date in the past. */
    OVERDUE,
    /** Returned. */
    RETURNED
}
