package com.lexhive.lending.book.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.ISBN;

public record BookRequest(
        @Schema(example = "Clean Architecture")
        @NotBlank @Size(max = 255) String title,

        @Schema(example = "Robert C. Martin")
        @NotBlank @Size(max = 255) String author,

        @Schema(description = "ISBN-10 or ISBN-13, hyphens allowed; stored without hyphens", example = "978-0134494166")
        @NotBlank @ISBN(type = ISBN.Type.ANY) String isbn,

        @Schema(description = "Physical copies owned by the library", example = "2")
        @NotNull @Min(0) @Max(10_000) Integer totalCopies) {
}
