package com.lexhive.lending.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexhive.lending.common.error.DomainException;
import com.lexhive.lending.common.error.DuplicateActiveLoanException;
import com.lexhive.lending.common.error.HasOverdueLoansException;
import com.lexhive.lending.common.error.LoanLimitExceededException;
import com.lexhive.lending.config.LoanPolicyProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LendingPolicyTest {

    private static final long MEMBER = 7;
    private static final long BOOK = 42;
    private static final Instant NOW = Instant.parse("2026-01-10T10:00:00Z");

    private final LendingPolicy policy = new LendingPolicy(new LoanPolicyProperties(3, Duration.ofDays(14)));

    @Nested
    class CheckCanBorrow {

        @ParameterizedTest(name = "{0} active loans is below the limit of 3")
        @ValueSource(longs = {0, 1, 2})
        void allowsBorrowingBelowTheLimit(long activeLoans) {
            assertThatCode(() -> policy.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(activeLoans, false, false)))
                    .doesNotThrowAnyException();
        }

        @Test
        void rejectsWhenActiveLoansReachTheLimit() {
            assertThatThrownBy(() -> policy.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(3, false, false)))
                    .isInstanceOf(LoanLimitExceededException.class)
                    .hasMessage("Member 7 already has 3 active loans (max 3).")
                    .extracting(e -> ((DomainException) e).code()).isEqualTo("LOAN_LIMIT_EXCEEDED");
        }

        @Test
        void rejectsMemberWithOverdueLoans() {
            assertThatThrownBy(() -> policy.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(1, true, false)))
                    .isInstanceOf(HasOverdueLoansException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo("HAS_OVERDUE_LOANS");
        }

        @Test
        void rejectsSecondActiveLoanOfTheSameBook() {
            assertThatThrownBy(() -> policy.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(1, false, true)))
                    .isInstanceOf(DuplicateActiveLoanException.class)
                    .extracting(e -> ((DomainException) e).code()).isEqualTo("DUPLICATE_ACTIVE_LOAN");
        }

        @Test
        void overdueIsReportedBeforeLimitAndDuplicate() {
            assertThatThrownBy(() -> policy.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(3, true, true)))
                    .isInstanceOf(HasOverdueLoansException.class);
        }

        @Test
        void limitIsReportedBeforeDuplicate() {
            assertThatThrownBy(() -> policy.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(3, false, true)))
                    .isInstanceOf(LoanLimitExceededException.class);
        }

        @Test
        void limitComesFromConfiguration() {
            var generous = new LendingPolicy(new LoanPolicyProperties(5, Duration.ofDays(14)));

            assertThatCode(() -> generous.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(4, false, false)))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> generous.checkCanBorrow(MEMBER, BOOK, new BorrowerStanding(5, false, false)))
                    .isInstanceOf(LoanLimitExceededException.class);
        }
    }

    @Nested
    class DueDate {

        @Test
        void isBorrowTimePlusConfiguredDuration() {
            assertThat(policy.dueDateFor(NOW)).isEqualTo(Instant.parse("2026-01-24T10:00:00Z"));
        }

        @Test
        void followsConfiguredDuration() {
            var weekly = new LendingPolicy(new LoanPolicyProperties(3, Duration.ofDays(7)));

            assertThat(weekly.dueDateFor(NOW)).isEqualTo(Instant.parse("2026-01-17T10:00:00Z"));
        }
    }

    @Nested
    class LoanStatusAt {

        private final Loan loan = new Loan(BOOK, MEMBER, NOW, NOW.plus(Duration.ofDays(14)));

        @Test
        void isActiveBeforeDueDate() {
            assertThat(loan.statusAt(NOW.plus(Duration.ofDays(13)))).isEqualTo(LoanStatus.ACTIVE);
        }

        @Test
        void isNotOverdueExactlyAtDueDate() {
            assertThat(loan.statusAt(loan.getDueDate())).isEqualTo(LoanStatus.ACTIVE);
        }

        @Test
        void isOverdueJustAfterDueDate() {
            assertThat(loan.statusAt(loan.getDueDate().plusNanos(1_000))).isEqualTo(LoanStatus.OVERDUE);
        }

        @Test
        void isReturnedOnceReturnedEvenIfLate() {
            loan.markReturned(loan.getDueDate().plus(Duration.ofDays(3)));

            assertThat(loan.statusAt(loan.getDueDate().plus(Duration.ofDays(10)))).isEqualTo(LoanStatus.RETURNED);
        }
    }

    @Nested
    class Configuration {

        @Test
        void rejectsNonPositiveDuration() {
            assertThatThrownBy(() -> new LoanPolicyProperties(3, Duration.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LoanPolicyProperties(3, Duration.ofDays(-1)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
