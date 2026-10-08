package com.lexhive.lending.book;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lexhive.lending.book.dto.BookRequest;
import com.lexhive.lending.book.dto.BookResponse;
import com.lexhive.lending.common.error.ResourceConflictException;
import com.lexhive.lending.common.error.ResourceNotFoundException;
import com.lexhive.lending.common.web.PageResponse;
import com.lexhive.lending.config.SecurityConfig;
import com.lexhive.lending.security.ProblemDetailSecurityHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BookController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class})
class BookControllerTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
    private static final BookResponse CLEAN_CODE =
            new BookResponse(1, "Clean Code", "Robert C. Martin", "9780132350884", 3, 2, CREATED, CREATED);

    private static final String VALID_BODY = """
            {"title": "Clean Code", "author": "Robert C. Martin", "isbn": "978-0132350884", "totalCopies": 3}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private BookService bookService;

    @Test
    @WithMockUser(roles = "MEMBER")
    void listsBooksForAnyAuthenticatedUser() throws Exception {
        when(bookService.search(eq("clean"), isNull(), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(CLEAN_CODE), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/books").param("title", "clean"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].isbn").value("9780132350884"))
                .andExpect(jsonPath("$.content[0].availableCopies").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void createsBookAndReturnsLocation() throws Exception {
        when(bookService.create(any(BookRequest.class))).thenReturn(CLEAN_CODE);

        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/books/1"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void rejectsInvalidBookWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content("""
                        {"title": " ", "author": "A", "isbn": "123-not-an-isbn", "totalCopies": -1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("title", "isbn", "totalCopies")))
                .andExpect(jsonPath("$.traceId").value(notNullValue()));
        verifyNoInteractions(bookService);
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content("{\"title\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void membersCannotCreateBooks() throws Exception {
        mvc.perform(post("/api/v1/books").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(bookService);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void unknownBookIsProblemDetail404WithCorrelationId() throws Exception {
        when(bookService.get(99)).thenThrow(ResourceNotFoundException.book(99));

        mvc.perform(get("/api/v1/books/99").header("X-Request-Id", "req-123"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "req-123"))
                .andExpect(jsonPath("$.type").value("https://lexhive.example/errors/book-not-found"))
                .andExpect(jsonPath("$.title").value("Book not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Book 99 not found."))
                .andExpect(jsonPath("$.code").value("BOOK_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").value("req-123"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void loweringCopiesBelowLoanedCountIsConflict() throws Exception {
        when(bookService.update(eq(1L), any(BookRequest.class))).thenThrow(ResourceConflictException.copiesOnLoan(1, 2, 1));

        mvc.perform(put("/api/v1/books/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPIES_ON_LOAN"));
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void nonNumericIdIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/books/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
