package com.lexhive.lending.member.dto;

import com.lexhive.lending.member.Member;
import java.time.Instant;

public record MemberResponse(long id, String name, String email, Instant createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getName(), member.getEmail(), member.getCreatedAt());
    }
}
