package com.lexhive.lending.security;

import com.lexhive.lending.loan.LoanRepository;
import com.lexhive.lending.member.MemberRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Ownership checks for MEMBER users, used from {@code @PreAuthorize} expressions, e.g.
 * {@code @memberAccess.isSelf(authentication, #id)}. A MEMBER user is linked to a member record by email.
 */
@Component("memberAccess")
public class MemberAccess {

    private static final String MEMBER_AUTHORITY = "ROLE_" + Role.MEMBER.name();

    private final MemberRepository members;
    private final LoanRepository loans;

    public MemberAccess(MemberRepository members, LoanRepository loans) {
        this.members = members;
        this.loans = loans;
    }

    /** True if the caller is a MEMBER user whose email matches the member with this id. */
    public boolean isSelf(Authentication authentication, Long memberId) {
        return memberId != null && memberEmail(authentication)
                .map(email -> members.existsByIdAndEmail(memberId, email))
                .orElse(false);
    }

    /** True if the caller is a MEMBER user who borrowed this loan. */
    public boolean ownsLoan(Authentication authentication, Long loanId) {
        return loanId != null && memberEmail(authentication)
                .map(email -> loans.isOwnedByMemberEmail(loanId, email))
                .orElse(false);
    }

    private static Optional<String> memberEmail(Authentication authentication) {
        if (authentication != null
                && authentication.getPrincipal() instanceof UserDetails user
                && user.getAuthorities().stream().anyMatch(a -> MEMBER_AUTHORITY.equals(a.getAuthority()))) {
            return Optional.of(user.getUsername().toLowerCase(Locale.ROOT));
        }
        return Optional.empty();
    }
}
