package com.lexhive.lending.book;

import com.lexhive.lending.book.dto.BookRequest;
import com.lexhive.lending.book.dto.BookResponse;
import com.lexhive.lending.common.error.ResourceConflictException;
import com.lexhive.lending.common.error.ResourceNotFoundException;
import com.lexhive.lending.common.web.PageResponse;
import com.lexhive.lending.loan.LoanRepository;
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
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository books;
    private final LoanRepository loans;
    private final Clock clock;

    public BookService(BookRepository books, LoanRepository loans, Clock clock) {
        this.books = books;
        this.loans = loans;
        this.clock = clock;
    }

    public PageResponse<BookResponse> search(String title, String author, Pageable pageable) {
        return PageResponse.of(books.findAll(BookSpecifications.matching(title, author), pageable), BookResponse::from);
    }

    public BookResponse get(long id) {
        return books.findById(id)
                .map(BookResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.book(id));
    }

    @Transactional
    public BookResponse create(BookRequest request) {
        String isbn = normalizeIsbn(request.isbn());
        if (books.existsByIsbn(isbn)) {
            throw ResourceConflictException.duplicateIsbn(isbn);
        }
        var book = books.save(new Book(request.title().trim(), request.author().trim(), isbn,
                request.totalCopies(), Instant.now(clock)));
        log.info("book.created bookId={} isbn={} totalCopies={}", book.getId(), isbn, book.getTotalCopies());
        return BookResponse.from(book);
    }

    /** Locks the row so the copies-on-loan calculation cannot race with a concurrent borrow or return. */
    @Transactional
    public BookResponse update(long id, BookRequest request) {
        var book = books.findByIdForUpdate(id).orElseThrow(() -> ResourceNotFoundException.book(id));
        String isbn = normalizeIsbn(request.isbn());
        if (books.existsByIsbnAndIdNot(isbn, id)) {
            throw ResourceConflictException.duplicateIsbn(isbn);
        }
        book.update(request.title().trim(), request.author().trim(), isbn, request.totalCopies(), Instant.now(clock));
        log.info("book.updated bookId={} totalCopies={} availableCopies={}",
                id, book.getTotalCopies(), book.getAvailableCopies());
        return BookResponse.from(book);
    }

    /** Books with loans are kept so loan history stays intact (soft delete would be the next step). */
    @Transactional
    public void delete(long id) {
        var book = books.findByIdForUpdate(id).orElseThrow(() -> ResourceNotFoundException.book(id));
        if (loans.existsByBookIdAndReturnedAtIsNull(id)) {
            throw ResourceConflictException.activeLoansExist("Book", id);
        }
        if (loans.existsByBookId(id)) {
            throw ResourceConflictException.loanHistoryExists("Book", id);
        }
        books.delete(book);
        log.info("book.deleted bookId={}", id);
    }

    /** Stores ISBNs without separators so "978-0-13-235088-4" and "9780132350884" are the same book. */
    static String normalizeIsbn(String isbn) {
        return isbn.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
