package com.lexhive.lending.book;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexhive.lending.support.ApiClient;
import com.lexhive.lending.support.IntegrationTest;
import com.lexhive.lending.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Book and member management against PostgreSQL: uniqueness, stock and deletion guards, search. */
@IntegrationTest
class CatalogIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;

    private ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, json);
    }

    @Test
    void isbnIsNormalizedAndUnique() throws Exception {
        String isbn = TestData.isbn13();
        String hyphenated = isbn.substring(0, 3) + "-" + isbn.substring(3);

        api.postJson("/api/v1/books", bookJson("DDD", hyphenated, 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isbn").value(isbn));
        api.postJson("/api/v1/books", bookJson("DDD again", isbn, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ISBN"));
    }

    @Test
    void searchIsCaseInsensitiveAndTreatsWildcardsLiterally() throws Exception {
        String title = "Zebra_Unique%Title " + TestData.isbn13();
        api.postJson("/api/v1/books", bookJson(title, TestData.isbn13(), 1)).andExpect(status().isCreated());

        api.get("/api/v1/books?title={t}", "zebra_unique%title")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value(title));
        api.get("/api/v1/books?title={t}", "zebra%unique")
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void totalCopiesCannotDropBelowCopiesOnLoan() throws Exception {
        long book = api.createBook(3);
        api.borrowOk(book, api.createMember());
        api.borrowOk(book, api.createMember());
        String isbn = api.getJson("/api/v1/books/{id}", book).get("isbn").asText();

        updateBook(book, bookJson("Test Book", isbn, 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COPIES_ON_LOAN"));

        updateBook(book, bookJson("Test Book", isbn, 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCopies").value(2))
                .andExpect(jsonPath("$.availableCopies").value(0));

        updateBook(book, bookJson("Test Book", isbn, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableCopies").value(3));
    }

    @Test
    void bookWithLoansCannotBeDeleted() throws Exception {
        long book = api.createBook(1);
        long loan = api.borrowOk(book, api.createMember());

        deleteAs("/api/v1/books/{id}", book).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_LOANS_EXIST"));

        api.returnLoan(loan).andExpect(status().isOk());
        deleteAs("/api/v1/books/{id}", book).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_HISTORY_EXISTS"));
    }

    @Test
    void bookWithoutLoansCanBeDeleted() throws Exception {
        long book = api.createBook(1);

        deleteAs("/api/v1/books/{id}", book).andExpect(status().isNoContent());
        api.get("/api/v1/books/{id}", book).andExpect(status().isNotFound());
        deleteAs("/api/v1/books/{id}", book).andExpect(status().isNotFound());
    }

    @Test
    void memberEmailIsNormalizedAndUnique() throws Exception {
        String email = TestData.email();

        api.postJson("/api/v1/members", memberJson("Dana", email.toUpperCase()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
        api.postJson("/api/v1/members", memberJson("Dana twin", email))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    void memberCanBeUpdatedAndDeletedWhenNoLoans() throws Exception {
        long member = api.createMember();
        String newEmail = TestData.email();

        mvc.perform(put("/api/v1/members/{id}", member).with(ApiClient.LIBRARIAN)
                        .contentType(MediaType.APPLICATION_JSON).content(memberJson("Renamed", newEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.email").value(newEmail));

        deleteAs("/api/v1/members/{id}", member).andExpect(status().isNoContent());
        api.get("/api/v1/members/{id}", member).andExpect(status().isNotFound());
    }

    @Test
    void memberWithActiveLoanCannotBeDeleted() throws Exception {
        long member = api.createMember();
        api.borrowOk(api.createBook(1), member);

        deleteAs("/api/v1/members/{id}", member).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_LOANS_EXIST"));
    }

    @Test
    void unknownSortPropertyIsBadRequest() throws Exception {
        api.get("/api/v1/books?sort=nope").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"));
    }

    private ResultActions updateBook(long id, String body) throws Exception {
        return mvc.perform(put("/api/v1/books/{id}", id).with(ApiClient.LIBRARIAN)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deleteAs(String path, long id) throws Exception {
        return mvc.perform(delete(path, id).with(httpBasic("admin", "admin123")));
    }

    private static String bookJson(String title, String isbn, int copies) {
        return """
                {"title": "%s", "author": "Someone", "isbn": "%s", "totalCopies": %d}
                """.formatted(title, isbn, copies);
    }

    private static String memberJson(String name, String email) {
        return """
                {"name": "%s", "email": "%s"}
                """.formatted(name, email);
    }
}
