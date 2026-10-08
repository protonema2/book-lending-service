package com.lexhive.lending.loan;

import com.lexhive.lending.common.web.PageResponse;
import com.lexhive.lending.loan.dto.BorrowRequest;
import com.lexhive.lending.loan.dto.LoanResponse;
import com.lexhive.lending.security.StaffOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Loans", description = "Borrowing and returning books")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN') or @memberAccess.isSelf(authentication, #request.memberId())")
    @Operation(summary = "Borrow a book",
            description = """
                    LIBRARIAN, ADMIN, or a MEMBER borrowing for themself. Rules, checked in this order:
                    no overdue loans (HAS_OVERDUE_LOANS), fewer than the configured max active loans (LOAN_LIMIT_EXCEEDED),
                    no active loan of the same book (DUPLICATE_ACTIVE_LOAN), a copy available (BOOK_UNAVAILABLE).
                    Due date = now + configured loan duration.""")
    public ResponseEntity<LoanResponse> borrow(@Valid @RequestBody BorrowRequest request) {
        LoanResponse loan = loanService.borrow(request.bookId(), request.memberId());
        return ResponseEntity.created(URI.create("/api/v1/loans/" + loan.id())).body(loan);
    }

    @PostMapping("/loans/{id}/return")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN') or @memberAccess.ownsLoan(authentication, #id)")
    @Operation(summary = "Return a book", description = "LIBRARIAN, ADMIN, or the MEMBER who borrowed it. 409 LOAN_ALREADY_RETURNED if returned before.")
    public LoanResponse returnLoan(@PathVariable long id) {
        return loanService.returnLoan(id);
    }

    @GetMapping("/loans/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN') or @memberAccess.ownsLoan(authentication, #id)")
    @Operation(summary = "Get a loan", description = "LIBRARIAN, ADMIN, or the MEMBER who borrowed it.")
    public LoanResponse get(@PathVariable long id) {
        return loanService.get(id);
    }

    @GetMapping("/loans")
    @StaffOnly
    @Operation(summary = "Search loans", description = "LIBRARIAN or ADMIN. ACTIVE excludes overdue loans, matching the status field.")
    public PageResponse<LoanResponse> search(
            @Parameter(description = "ACTIVE, OVERDUE or RETURNED") @RequestParam(required = false) LoanStatus status,
            @RequestParam(required = false) Long memberId,
            @ParameterObject @PageableDefault(size = 20, sort = "borrowedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.search(status, memberId, pageable);
    }

    @GetMapping("/members/{memberId}/loans")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN') or @memberAccess.isSelf(authentication, #memberId)")
    @Tag(name = "Members")
    @Operation(summary = "A member's loans", description = "LIBRARIAN, ADMIN, or the member themself. Newest first.")
    public PageResponse<LoanResponse> loansOfMember(
            @PathVariable long memberId,
            @ParameterObject @PageableDefault(size = 20, sort = "borrowedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return loanService.loansOfMember(memberId, pageable);
    }
}
