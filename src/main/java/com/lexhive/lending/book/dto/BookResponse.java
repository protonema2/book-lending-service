package com.lexhive.lending.book.dto;

import com.lexhive.lending.book.Book;
import java.time.Instant;

public record BookResponse(
        long id,
        String title,
        String author,
        String isbn,
        int totalCopies,
        int availableCopies,
        Instant createdAt,
        Instant updatedAt) {

    public static BookResponse from(Book book) {
        return new BookResponse(book.getId(), book.getTitle(), book.getAuthor(), book.getIsbn(),
                book.getTotalCopies(), book.getAvailableCopies(), book.getCreatedAt(), book.getUpdatedAt());
    }
}
