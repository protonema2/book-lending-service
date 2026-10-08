package com.lexhive.lending.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexhive.lending.book.Book;
import com.lexhive.lending.book.BookRepository;
import com.lexhive.lending.member.Member;
import com.lexhive.lending.member.MemberRepository;
import com.lexhive.lending.support.PostgresContainerSupport;
import com.lexhive.lending.support.TestData;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Schema constraints and custom queries, verified against PostgreSQL (Flyway migrations applied). */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresContainerSupport.class)
class LoanRepositoryIT {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    private LoanRepository loans;
    @Autowired
    private BookRepository books;
    @Autowired
    private MemberRepository members;
    @Autowired
    private JdbcTemplate jdbc;

    private Book book;
    private Member member;

    @BeforeEach
    void setUp() {
        book = books.save(new Book("Refactoring Databases", "Ambler", TestData.isbn13(), 2, NOW));
        member = members.save(new Member("Eve", TestData.email(), NOW));
    }

    @Test
    void countsOnlyUnreturnedLoansAsActive() {
        loans.save(loan(book, NOW, NOW.plus(Duration.ofDays(14))));
        var returned = loan(books.save(new Book("Other", "X", TestData.isbn13(), 1, NOW)), NOW, NOW.plus(Duration.ofDays(14)));
        returned.markReturned(NOW.plusSeconds(60));
        loans.saveAndFlush(returned);

        assertThat(loans.countActiveByMember(member.getId())).isEqualTo(1);
        assertThat(loans.existsActiveByMemberAndBook(member.getId(), book.getId())).isTrue();
    }

    @Test
    void overdueMeansDueDateStrictlyBeforeNow() {
        Instant due = NOW.minus(Duration.ofDays(1));
        loans.saveAndFlush(loan(book, due.minus(Duration.ofDays(14)), due));

        assertThat(loans.existsOverdueByMember(member.getId(), due)).isFalse();
        assertThat(loans.existsOverdueByMember(member.getId(), due.plusNanos(1_000))).isTrue();
    }

    @Test
    void decrementStopsAtZero() {
        assertThat(books.decrementAvailableCopies(book.getId(), NOW)).isEqualTo(1);
        assertThat(books.decrementAvailableCopies(book.getId(), NOW)).isEqualTo(1);
        assertThat(books.decrementAvailableCopies(book.getId(), NOW)).isZero();
        assertThat(availableCopies(book)).isZero();
    }

    @Test
    void incrementStopsAtTotal() {
        assertThat(books.incrementAvailableCopies(book.getId(), NOW)).isZero();
        books.decrementAvailableCopies(book.getId(), NOW);
        assertThat(books.incrementAvailableCopies(book.getId(), NOW)).isEqualTo(1);
        assertThat(availableCopies(book)).isEqualTo(2);
    }

    @Test
    void ownershipIsMatchedByMemberEmail() {
        var saved = loans.saveAndFlush(loan(book, NOW, NOW.plus(Duration.ofDays(14))));

        assertThat(loans.isOwnedByMemberEmail(saved.getId(), member.getEmail())).isTrue();
        assertThat(loans.isOwnedByMemberEmail(saved.getId(), "someone.else@example.com")).isFalse();
    }

    @Test
    void databaseAllowsOnlyOneActiveLoanPerMemberAndBook() {
        loans.saveAndFlush(loan(book, NOW, NOW.plus(Duration.ofDays(14))));

        assertThatThrownBy(() -> loans.saveAndFlush(loan(book, NOW, NOW.plus(Duration.ofDays(14)))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_loan_active_member_book");
    }

    @Test
    void returnedLoansDoNotBlockANewLoanOfTheSameBook() {
        var first = loan(book, NOW, NOW.plus(Duration.ofDays(14)));
        first.markReturned(NOW.plusSeconds(1));
        loans.saveAndFlush(first);

        loans.saveAndFlush(loan(book, NOW.plusSeconds(2), NOW.plus(Duration.ofDays(14))));

        assertThat(loans.countActiveByMember(member.getId())).isEqualTo(1);
    }

    @Test
    void isbnIsUnique() {
        assertThatThrownBy(() -> books.saveAndFlush(new Book("Copy", "Cat", book.getIsbn(), 1, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_book_isbn");
    }

    @Test
    void emailIsUnique() {
        assertThatThrownBy(() -> members.saveAndFlush(new Member("Twin", member.getEmail(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_member_email");
    }

    @Test
    void availableCopiesCannotExceedTotal() {
        assertThatThrownBy(() -> jdbc.update("update book set available_copies = 3 where id = ?", book.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_available_range");
    }

    @Test
    void dueDateMustBeAfterBorrowDate() {
        assertThatThrownBy(() -> loans.saveAndFlush(loan(book, NOW, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_due_after_borrow");
    }

    private Loan loan(Book target, Instant borrowedAt, Instant dueDate) {
        return new Loan(target.getId(), member.getId(), borrowedAt, dueDate);
    }

    private int availableCopies(Book target) {
        return jdbc.queryForObject("select available_copies from book where id = ?", Integer.class, target.getId());
    }
}
