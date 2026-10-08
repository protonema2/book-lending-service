package com.lexhive.lending.loan;

import com.lexhive.lending.common.error.LoanAlreadyReturnedException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A copy of a book lent to a member. References book and member by id (FKs enforced in the schema), which keeps
 * the aggregate small and avoids lazy-loading surprises with open-in-view disabled.
 */
@Entity
@Table(name = "loan")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long bookId;
    private long memberId;
    private Instant borrowedAt;
    private Instant dueDate;
    private Instant returnedAt;

    protected Loan() {
        // for JPA
    }

    public Loan(long bookId, long memberId, Instant borrowedAt, Instant dueDate) {
        this.bookId = bookId;
        this.memberId = memberId;
        this.borrowedAt = borrowedAt;
        this.dueDate = dueDate;
    }

    public void markReturned(Instant now) {
        if (isReturned()) {
            throw new LoanAlreadyReturnedException(id);
        }
        this.returnedAt = now;
    }

    public boolean isReturned() {
        return returnedAt != null;
    }

    /** A loan due exactly {@code now} is not overdue yet ({@code due_date < now()}). */
    public LoanStatus statusAt(Instant now) {
        if (isReturned()) {
            return LoanStatus.RETURNED;
        }
        return dueDate.isBefore(now) ? LoanStatus.OVERDUE : LoanStatus.ACTIVE;
    }

    public Long getId() {
        return id;
    }

    public long getBookId() {
        return bookId;
    }

    public long getMemberId() {
        return memberId;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getDueDate() {
        return dueDate;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}
