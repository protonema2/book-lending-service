package com.lexhive.lending.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexhive.lending.support.ApiClient;
import com.lexhive.lending.support.IntegrationTest;
import com.lexhive.lending.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-end lending scenarios through HTTP, PostgreSQL and a controllable clock. */
@IntegrationTest
class LoanFlowIT {

    private static final Duration LOAN_DURATION = Duration.ofDays(14);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private MutableClock clock;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, json);
    }

    @Test
    void borrowOverdueBlockedReturnBorrowAgain() throws Exception {
        long member = api.createMember();
        long book = api.createBook(2);
        long otherBook = api.createBook(1);
        Instant borrowedAt = clock.instant();

        long loan = api.borrowOk(book, member);
        var created = api.getJson("/api/v1/loans/{id}", loan);
        assertThat(Instant.parse(created.get("dueDate").asText())).isEqualTo(borrowedAt.plus(LOAN_DURATION));
        assertThat(api.getJson("/api/v1/books/{id}", book).get("availableCopies").asInt()).isEqualTo(1);

        // Exactly at the due date the loan is not overdue yet.
        clock.advance(LOAN_DURATION);
        api.get("/api/v1/loans/{id}", loan).andExpect(jsonPath("$.status").value("ACTIVE"));

        clock.advance(Duration.ofSeconds(1));
        api.get("/api/v1/loans/{id}", loan).andExpect(jsonPath("$.status").value("OVERDUE"));
        api.get("/api/v1/loans?status=OVERDUE&memberId={m}", member)
                .andExpect(jsonPath("$.content[0].id").value(loan));

        api.borrow(otherBook, member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_OVERDUE_LOANS"));

        api.returnLoan(loan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));
        assertThat(api.getJson("/api/v1/books/{id}", book).get("availableCopies").asInt()).isEqualTo(2);

        api.borrowOk(otherBook, member);
        api.borrowOk(book, member);
        assertThat(api.getJson("/api/v1/members/{id}/loans", member).get("totalElements").asInt()).isEqualTo(3);
    }

    @Test
    void activeLoanLimitIsEnforcedAndFreedByReturn() throws Exception {
        long member = api.createMember();
        long first = api.borrowOk(api.createBook(1), member);
        api.borrowOk(api.createBook(1), member);
        api.borrowOk(api.createBook(1), member);
        long fourthBook = api.createBook(1);

        api.borrow(fourthBook, member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.detail").value("Member %d already has 3 active loans (max 3).".formatted(member)));

        api.returnLoan(first).andExpect(status().isOk());
        api.borrowOk(fourthBook, member);
    }

    @Test
    void lastCopyCannotBeLentTwice() throws Exception {
        long book = api.createBook(1);
        long alice = api.createMember();
        long bob = api.createMember();

        long loan = api.borrowOk(book, alice);
        api.borrow(book, bob)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOK_UNAVAILABLE"));

        api.returnLoan(loan).andExpect(status().isOk());
        api.borrowOk(book, bob);
    }

    @Test
    void memberCannotHoldTwoCopiesOfTheSameBook() throws Exception {
        long book = api.createBook(3);
        long member = api.createMember();
        api.borrowOk(book, member);

        api.borrow(book, member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACTIVE_LOAN"));
    }

    @Test
    void loanCannotBeReturnedTwice() throws Exception {
        long loan = api.borrowOk(api.createBook(1), api.createMember());
        api.returnLoan(loan).andExpect(status().isOk());

        api.returnLoan(loan)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_ALREADY_RETURNED"));
    }

    @Test
    void unknownBookMemberAndLoanAre404() throws Exception {
        long member = api.createMember();
        long book = api.createBook(1);

        api.borrow(Long.MAX_VALUE, member).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"));
        api.borrow(book, Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        api.returnLoan(Long.MAX_VALUE).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
        api.get("/api/v1/members/{id}/loans", Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    @Test
    void loanSearchFiltersByStatus() throws Exception {
        long member = api.createMember();
        long returned = api.borrowOk(api.createBook(1), member);
        long active = api.borrowOk(api.createBook(1), member);
        api.returnLoan(returned).andExpect(status().isOk());

        assertThat(loanIds("ACTIVE", member)).containsExactly(active);
        assertThat(loanIds("RETURNED", member)).containsExactly(returned);
        assertThat(loanIds("OVERDUE", member)).isEmpty();
    }

    private long[] loanIds(String status, long member) throws Exception {
        var page = api.getJson("/api/v1/loans?status={s}&memberId={m}", status, member);
        return StreamSupport.stream(page.get("content").spliterator(), false)
                .mapToLong(loan -> loan.get("id").asLong())
                .toArray();
    }
}
