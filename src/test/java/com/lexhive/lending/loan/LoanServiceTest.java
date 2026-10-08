package com.lexhive.lending.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lexhive.lending.book.BookRepository;
import com.lexhive.lending.common.error.DomainException;
import com.lexhive.lending.common.error.BookUnavailableException;
import com.lexhive.lending.common.error.HasOverdueLoansException;
import com.lexhive.lending.common.error.LoanAlreadyReturnedException;
import com.lexhive.lending.common.error.LoanLimitExceededException;
import com.lexhive.lending.common.error.ResourceNotFoundException;
import com.lexhive.lending.config.LoanPolicyProperties;
import com.lexhive.lending.member.Member;
import com.lexhive.lending.member.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final long BOOK = 10;
    private static final long MEMBER = 20;
    private static final Instant NOW = Instant.parse("2026-03-01T09:00:00Z");

    @Mock
    private LoanRepository loans;
    @Mock
    private BookRepository books;
    @Mock
    private MemberRepository members;
    @Mock
    private LoanMetrics metrics;

    private LoanService service;

    @BeforeEach
    void setUp() {
        var policy = new LendingPolicy(new LoanPolicyProperties(3, Duration.ofDays(14)));
        service = new LoanService(loans, books, members, policy, metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Nested
    class Borrow {

        @BeforeEach
        void memberAndBookExist() {
            when(members.findByIdForUpdate(MEMBER)).thenReturn(Optional.of(new Member("Ann", "ann@example.com", NOW)));
            when(books.existsById(BOOK)).thenReturn(true);
        }

        @Test
        void createsLoanDueAfterConfiguredDurationAndTakesACopy() {
            givenStanding(0, false, false);
            when(books.decrementAvailableCopies(BOOK, NOW)).thenReturn(1);
            when(loans.save(any(Loan.class))).thenAnswer(inv -> withId(inv.getArgument(0), 99L));

            var response = service.borrow(BOOK, MEMBER);

            assertThat(response.id()).isEqualTo(99L);
            assertThat(response.borrowedAt()).isEqualTo(NOW);
            assertThat(response.dueDate()).isEqualTo(NOW.plus(Duration.ofDays(14)));
            assertThat(response.status()).isEqualTo(LoanStatus.ACTIVE);
            verify(metrics).borrowed();
        }

        @Test
        void rejectsOverdueMemberWithoutTouchingStock() {
            givenStanding(1, true, false);

            assertThatThrownBy(() -> service.borrow(BOOK, MEMBER)).isInstanceOf(HasOverdueLoansException.class);

            verify(books, never()).decrementAvailableCopies(anyLong(), any());
            verify(loans, never()).save(any());
            verify(metrics).rejected("HAS_OVERDUE_LOANS");
        }

        @Test
        void rejectsMemberAtTheLimit() {
            givenStanding(3, false, false);

            assertThatThrownBy(() -> service.borrow(BOOK, MEMBER)).isInstanceOf(LoanLimitExceededException.class);

            verify(metrics).rejected("LOAN_LIMIT_EXCEEDED");
            verify(loans, never()).save(any());
        }

        @Test
        void rejectsWhenNoCopyCouldBeTaken() {
            givenStanding(0, false, false);
            when(books.decrementAvailableCopies(BOOK, NOW)).thenReturn(0);

            assertThatThrownBy(() -> service.borrow(BOOK, MEMBER)).isInstanceOf(BookUnavailableException.class);

            verify(loans, never()).save(any());
            verify(metrics).rejected("BOOK_UNAVAILABLE");
            verify(metrics, never()).borrowed();
        }

        private void givenStanding(long active, boolean overdue, boolean holdsBook) {
            when(loans.countActiveByMember(MEMBER)).thenReturn(active);
            when(loans.existsOverdueByMember(MEMBER, NOW)).thenReturn(overdue);
            when(loans.existsActiveByMemberAndBook(MEMBER, BOOK)).thenReturn(holdsBook);
        }
    }

    @Test
    void borrowFailsForUnknownMemberWithoutCountingARejection() {
        when(members.findByIdForUpdate(MEMBER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrow(BOOK, MEMBER))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("MEMBER_NOT_FOUND");
        verify(metrics, never()).rejected(anyString());
    }

    @Test
    void borrowFailsForUnknownBook() {
        when(members.findByIdForUpdate(MEMBER)).thenReturn(Optional.of(new Member("Ann", "ann@example.com", NOW)));
        when(books.existsById(BOOK)).thenReturn(false);

        assertThatThrownBy(() -> service.borrow(BOOK, MEMBER))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(e -> ((DomainException) e).code()).isEqualTo("BOOK_NOT_FOUND");
    }

    @Nested
    class Return {

        @Test
        void marksReturnedAndPutsCopyBack() {
            var loan = withId(new Loan(BOOK, MEMBER, NOW.minus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(11))), 5L);
            when(loans.findByIdForUpdate(5L)).thenReturn(Optional.of(loan));
            when(books.incrementAvailableCopies(BOOK, NOW)).thenReturn(1);

            var response = service.returnLoan(5L);

            assertThat(response.returnedAt()).isEqualTo(NOW);
            assertThat(response.status()).isEqualTo(LoanStatus.RETURNED);
            verify(metrics).returned();
        }

        @Test
        void rejectsSecondReturnWithoutTouchingStock() {
            var loan = withId(new Loan(BOOK, MEMBER, NOW.minus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(11))), 5L);
            loan.markReturned(NOW.minus(Duration.ofDays(1)));
            when(loans.findByIdForUpdate(5L)).thenReturn(Optional.of(loan));

            assertThatThrownBy(() -> service.returnLoan(5L)).isInstanceOf(LoanAlreadyReturnedException.class);

            verify(books, never()).incrementAvailableCopies(anyLong(), any());
            verify(metrics).rejected("LOAN_ALREADY_RETURNED");
        }

        @Test
        void failsLoudlyIfStockIsInconsistent() {
            var loan = withId(new Loan(BOOK, MEMBER, NOW.minus(Duration.ofDays(3)), NOW.plus(Duration.ofDays(11))), 5L);
            when(loans.findByIdForUpdate(5L)).thenReturn(Optional.of(loan));
            when(books.incrementAvailableCopies(BOOK, NOW)).thenReturn(0);

            assertThatThrownBy(() -> service.returnLoan(5L)).isInstanceOf(IllegalStateException.class);
            verify(metrics, never()).returned();
        }

        @Test
        void failsForUnknownLoan() {
            when(loans.findByIdForUpdate(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.returnLoan(5L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo("LOAN_NOT_FOUND");
        }
    }

    @Test
    void savedLoanCarriesBorrowTimeAndDueDate() {
        when(members.findByIdForUpdate(MEMBER)).thenReturn(Optional.of(new Member("Ann", "ann@example.com", NOW)));
        when(books.existsById(BOOK)).thenReturn(true);
        when(books.decrementAvailableCopies(BOOK, NOW)).thenReturn(1);
        when(loans.save(any(Loan.class))).thenAnswer(inv -> withId(inv.getArgument(0), 1L));

        service.borrow(BOOK, MEMBER);

        var captor = ArgumentCaptor.forClass(Loan.class);
        verify(loans).save(captor.capture());
        assertThat(captor.getValue().getBookId()).isEqualTo(BOOK);
        assertThat(captor.getValue().getMemberId()).isEqualTo(MEMBER);
        assertThat(captor.getValue().getDueDate()).isEqualTo(Instant.parse("2026-03-15T09:00:00Z"));
    }

    private static Loan withId(Loan loan, long id) {
        ReflectionTestUtils.setField(loan, "id", id);
        return loan;
    }
}
