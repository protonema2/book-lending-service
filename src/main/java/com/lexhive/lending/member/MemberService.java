package com.lexhive.lending.member;

import com.lexhive.lending.common.error.ResourceConflictException;
import com.lexhive.lending.common.error.ResourceNotFoundException;
import com.lexhive.lending.common.web.PageResponse;
import com.lexhive.lending.loan.LoanRepository;
import com.lexhive.lending.member.dto.MemberRequest;
import com.lexhive.lending.member.dto.MemberResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepository members;
    private final LoanRepository loans;
    private final Clock clock;

    public MemberService(MemberRepository members, LoanRepository loans, Clock clock) {
        this.members = members;
        this.loans = loans;
        this.clock = clock;
    }

    public PageResponse<MemberResponse> list(Pageable pageable) {
        return PageResponse.of(members.findAll(pageable), MemberResponse::from);
    }

    public MemberResponse get(long id) {
        return members.findById(id)
                .map(MemberResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.member(id));
    }

    @Transactional
    public MemberResponse create(MemberRequest request) {
        String email = normalizeEmail(request.email());
        if (members.existsByEmail(email)) {
            throw ResourceConflictException.duplicateEmail(email);
        }
        var member = members.save(new Member(request.name().trim(), email, Instant.now(clock)));
        log.info("member.created memberId={}", member.getId());
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse update(long id, MemberRequest request) {
        var member = members.findById(id).orElseThrow(() -> ResourceNotFoundException.member(id));
        String email = normalizeEmail(request.email());
        if (members.existsByEmailAndIdNot(email, id)) {
            throw ResourceConflictException.duplicateEmail(email);
        }
        member.update(request.name().trim(), email);
        log.info("member.updated memberId={}", id);
        return MemberResponse.from(member);
    }

    /** Locks the member row (as borrow does) so a delete cannot interleave with a borrow by the same member. */
    @Transactional
    public void delete(long id) {
        var member = members.findByIdForUpdate(id).orElseThrow(() -> ResourceNotFoundException.member(id));
        if (loans.existsByMemberIdAndReturnedAtIsNull(id)) {
            throw ResourceConflictException.activeLoansExist("Member", id);
        }
        if (loans.existsByMemberId(id)) {
            throw ResourceConflictException.loanHistoryExists("Member", id);
        }
        members.delete(member);
        log.info("member.deleted memberId={}", id);
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
