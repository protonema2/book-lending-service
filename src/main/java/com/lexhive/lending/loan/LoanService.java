package com.lexhive.lending.loan;

import com.lexhive.lending.book.BookRepository;
import com.lexhive.lending.common.error.BookUnavailableException;
import com.lexhive.lending.common.error.LendingException;
import com.lexhive.lending.common.error.ResourceNotFoundException;
import com.lexhive.lending.common.web.PageResponse;
import com.lexhive.lending.loan.dto.LoanResponse;
import com.lexhive.lending.member.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrow and return transactions.
 *
 * <p>Concurrency: borrow locks the member row ({@code SELECT ... FOR UPDATE}) so the limit/overdue/duplicate
 * checks and the insert are atomic per member, and takes a copy with a conditional {@code UPDATE} so the
 * last copy of a book can only be lent once. Return locks the loan row so it cannot be returned twice.
 * Lock order is always member/loan first, then book, which rules out deadlocks between these paths.
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private static final Logger log = LoggerFactory.getLogger(LoanService.class);

    private final LoanRepository loans;
    private final BookRepository books;
    private final MemberRepository members;
    private final LendingPolicy policy;
    private final LoanMetrics metrics;
    private final Clock clock;

    public LoanService(LoanRepository loans, BookRepository books, MemberRepository members,
                       LendingPolicy policy, LoanMetrics metrics, Clock clock) {
        this.loans = loans;
        this.books = books;
        this.members = members;
        this.policy = policy;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public LoanResponse borrow(long bookId, long memberId) {
        members.findByIdForUpdate(memberId).orElseThrow(() -> ResourceNotFoundException.member(memberId));
        if (!books.existsById(bookId)) {
            throw ResourceNotFoundException.book(bookId);
        }
        Instant now = Instant.now(clock);
        try {
            var standing = new BorrowerStanding(
                    loans.countActiveByMember(memberId),
                    loans.existsOverdueByMember(memberId, now),
                    loans.existsActiveByMemberAndBook(memberId, bookId));
            policy.checkCanBorrow(memberId, bookId, standing);
            if (books.decrementAvailableCopies(bookId, now) == 0) {
                throw new BookUnavailableException(bookId);
            }
        } catch (LendingException e) {
            throw rejected(e, "bookId=" + bookId + " memberId=" + memberId);
        }
        var loan = loans.save(new Loan(bookId, memberId, now, policy.dueDateFor(now)));
        metrics.borrowed();
        log.info("loan.borrowed loanId={} bookId={} memberId={} dueDate={}", loan.getId(), bookId, memberId, loan.getDueDate());
        return LoanResponse.from(loan, now);
    }

    @Transactional
    public LoanResponse returnLoan(long loanId) {
        var loan = loans.findByIdForUpdate(loanId).orElseThrow(() -> ResourceNotFoundException.loan(loanId));
        Instant now = Instant.now(clock);
        boolean wasOverdue = loan.statusAt(now) == LoanStatus.OVERDUE;
        try {
            loan.markReturned(now);
        } catch (LendingException e) {
            throw rejected(e, "loanId=" + loanId);
        }
        if (books.incrementAvailableCopies(loan.getBookId(), now) == 0) {
            throw new IllegalStateException("Book %d has no copies on loan; stock is inconsistent".formatted(loan.getBookId()));
        }
        metrics.returned();
        log.info("loan.returned loanId={} bookId={} memberId={} overdue={}", loanId, loan.getBookId(), loan.getMemberId(), wasOverdue);
        return LoanResponse.from(loan, now);
    }

    public LoanResponse get(long loanId) {
        return loans.findById(loanId)
                .map(loan -> LoanResponse.from(loan, Instant.now(clock)))
                .orElseThrow(() -> ResourceNotFoundException.loan(loanId));
    }

    public PageResponse<LoanResponse> search(LoanStatus status, Long memberId, Pageable pageable) {
        Instant now = Instant.now(clock);
        return PageResponse.of(loans.findAll(LoanSpecifications.matching(status, memberId, now), pageable),
                loan -> LoanResponse.from(loan, now));
    }

    public PageResponse<LoanResponse> loansOfMember(long memberId, Pageable pageable) {
        if (!members.existsById(memberId)) {
            throw ResourceNotFoundException.member(memberId);
        }
        Instant now = Instant.now(clock);
        return PageResponse.of(loans.findByMemberId(memberId, pageable), loan -> LoanResponse.from(loan, now));
    }

    private LendingException rejected(LendingException e, String context) {
        metrics.rejected(e.code());
        log.warn("loan.rejected reason={} {}", e.code(), context);
        return e;
    }
}
