package com.lexhive.lending.loan;

/**
 * Snapshot of a member's loans at the time of a borrow request, as read from the database.
 *
 * @param activeLoans      number of unreturned loans (overdue ones included)
 * @param hasOverdueLoans  whether any unreturned loan is past its due date
 * @param alreadyHoldsBook whether the member has an unreturned loan of the requested book
 */
public record BorrowerStanding(long activeLoans, boolean hasOverdueLoans, boolean alreadyHoldsBook) {
}
