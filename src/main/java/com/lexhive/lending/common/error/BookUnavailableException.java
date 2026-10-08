package com.lexhive.lending.common.error;

public final class BookUnavailableException extends LendingException {

    public BookUnavailableException(long bookId) {
        super("No copies of book %d are currently available.".formatted(bookId));
    }

    @Override
    public String code() {
        return "BOOK_UNAVAILABLE";
    }
}
