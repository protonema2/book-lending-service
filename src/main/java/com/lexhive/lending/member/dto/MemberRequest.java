package com.lexhive.lending.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberRequest(
        @Schema(example = "Dave Miller")
        @NotBlank @Size(max = 255) String name,

        @Schema(description = "Unique, stored lower-case; MEMBER logins use it as username", example = "dave@example.com")
        @NotBlank @Email @Size(max = 255) String email) {
}
