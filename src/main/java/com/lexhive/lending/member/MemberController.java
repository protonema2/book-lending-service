package com.lexhive.lending.member;

import com.lexhive.lending.common.web.PageResponse;
import com.lexhive.lending.member.dto.MemberRequest;
import com.lexhive.lending.member.dto.MemberResponse;
import com.lexhive.lending.security.StaffOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Members", description = "Library member management")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    @StaffOnly
    @Operation(summary = "List members", description = "LIBRARIAN or ADMIN.")
    public PageResponse<MemberResponse> list(@ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return memberService.list(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('LIBRARIAN', 'ADMIN') or @memberAccess.isSelf(authentication, #id)")
    @Operation(summary = "Get a member", description = "LIBRARIAN, ADMIN, or the member themself.")
    public MemberResponse get(@PathVariable long id) {
        return memberService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @StaffOnly
    @Operation(summary = "Register a member", description = "LIBRARIAN or ADMIN. Email must be unique (409 DUPLICATE_EMAIL).")
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        MemberResponse created = memberService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/members/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @StaffOnly
    @Operation(summary = "Update a member", description = "LIBRARIAN or ADMIN.")
    public MemberResponse update(@PathVariable long id, @Valid @RequestBody MemberRequest request) {
        return memberService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @StaffOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a member", description = "LIBRARIAN or ADMIN. Rejected with 409 if the member has active loans or loan history.")
    public void delete(@PathVariable long id) {
        memberService.delete(id);
    }
}
