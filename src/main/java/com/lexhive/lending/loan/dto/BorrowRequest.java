package com.lexhive.lending.loan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BorrowRequest(
        @Schema(example = "1") @NotNull @Positive Long bookId,
        @Schema(description = "MEMBER users may only borrow for themselves", example = "1") @NotNull @Positive Long memberId) {
}
