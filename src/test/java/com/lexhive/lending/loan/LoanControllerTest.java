package com.lexhive.lending.loan;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lexhive.lending.common.error.HasOverdueLoansException;
import com.lexhive.lending.common.error.LoanLimitExceededException;
import com.lexhive.lending.config.SecurityConfig;
import com.lexhive.lending.loan.dto.LoanResponse;
import com.lexhive.lending.security.MemberAccess;
import com.lexhive.lending.security.ProblemDetailSecurityHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(LoanController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class})
class LoanControllerTest {

    private static final Instant NOW = Instant.parse("2026-02-01T12:00:00Z");
    private static final LoanResponse LOAN =
            new LoanResponse(5, 1, 2, NOW, Instant.parse("2026-02-15T12:00:00Z"), null, LoanStatus.ACTIVE);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LoanService loanService;

    @MockitoBean(name = "memberAccess")
    private MemberAccess memberAccess;

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void borrowReturnsCreatedLoan() throws Exception {
        when(loanService.borrow(1, 2)).thenReturn(LOAN);

        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookId": 1, "memberId": 2}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/loans/5"))
                .andExpect(jsonPath("$.dueDate").value("2026-02-15T12:00:00Z"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void ruleViolationIsConflictProblemDetail() throws Exception {
        when(loanService.borrow(1, 2)).thenThrow(new LoanLimitExceededException(2, 3, 3));

        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookId": 1, "memberId": 2}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://lexhive.example/errors/loan-limit-exceeded"))
                .andExpect(jsonPath("$.title").value("Loan limit exceeded"))
                .andExpect(jsonPath("$.detail").value("Member 2 already has 3 active loans (max 3)."))
                .andExpect(jsonPath("$.code").value("LOAN_LIMIT_EXCEEDED"));
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void borrowRequiresBookAndMember() throws Exception {
        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookId": 1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("memberId"));
        verifyNoInteractions(loanService);
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = "MEMBER")
    void memberCanBorrowForThemself() throws Exception {
        when(memberAccess.isSelf(any(), eq(2L))).thenReturn(true);
        when(loanService.borrow(1, 2)).thenReturn(LOAN);

        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookId": 1, "memberId": 2}
                        """))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = "MEMBER")
    void memberCannotBorrowForSomeoneElse() throws Exception {
        when(memberAccess.isSelf(any(), eq(3L))).thenReturn(false);

        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookId": 1, "memberId": 3}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(loanService);
    }

    @Test
    @WithMockUser(username = "alice@example.com", roles = "MEMBER")
    void memberWithOverdueLoansGetsConflict() throws Exception {
        when(memberAccess.isSelf(any(), eq(2L))).thenReturn(true);
        when(loanService.borrow(1, 2)).thenThrow(new HasOverdueLoansException(2));

        mvc.perform(post("/api/v1/loans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"bookId": 1, "memberId": 2}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HAS_OVERDUE_LOANS"));
    }

    @Test
    @WithMockUser(username = "bob@example.com", roles = "MEMBER")
    void memberCannotReturnSomeoneElsesLoan() throws Exception {
        when(memberAccess.ownsLoan(any(), eq(5L))).thenReturn(false);

        mvc.perform(post("/api/v1/loans/5/return"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(loanService);
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void membersCannotSearchAllLoans() throws Exception {
        mvc.perform(get("/api/v1/loans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void unknownStatusFilterIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/loans").param("status", "LOST"))
                .andExpect(status().isBadRequest());
    }
}
