package com.lexhive.lending.book;

import com.lexhive.lending.book.dto.BookRequest;
import com.lexhive.lending.book.dto.BookResponse;
import com.lexhive.lending.common.web.PageResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Books", description = "Catalog management")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    @Operation(summary = "List books", description = "Paginated; optional case-insensitive filters on title and author. Any authenticated user.")
    public PageResponse<BookResponse> list(@RequestParam(required = false) String title,
                                           @RequestParam(required = false) String author,
                                           @ParameterObject @PageableDefault(size = 20, sort = "title") Pageable pageable) {
        return bookService.search(title, author, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a book", description = "Any authenticated user.")
    public BookResponse get(@PathVariable long id) {
        return bookService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @StaffOnly
    @Operation(summary = "Add a book", description = "LIBRARIAN or ADMIN. All copies start available.")
    public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request) {
        BookResponse created = bookService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/books/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @StaffOnly
    @Operation(summary = "Update a book",
            description = "LIBRARIAN or ADMIN. totalCopies cannot go below the number of copies currently on loan (409 COPIES_ON_LOAN).")
    public BookResponse update(@PathVariable long id, @Valid @RequestBody BookRequest request) {
        return bookService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @StaffOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a book", description = "LIBRARIAN or ADMIN. Rejected with 409 if the book has active loans or loan history.")
    public void delete(@PathVariable long id) {
        bookService.delete(id);
    }
}
