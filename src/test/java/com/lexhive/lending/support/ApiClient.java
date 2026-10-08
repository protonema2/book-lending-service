package com.lexhive.lending.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Small helper for integration tests: calls the API as the librarian and parses JSON responses. */
public class ApiClient {

    public static final RequestPostProcessor LIBRARIAN = httpBasic("librarian", "librarian123");

    private final MockMvc mvc;
    private final ObjectMapper json;

    public ApiClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public long createBook(int copies) throws Exception {
        String body = """
                {"title": "Test Book", "author": "Test Author", "isbn": "%s", "totalCopies": %d}
                """.formatted(TestData.isbn13(), copies);
        return read(postJson("/api/v1/books", body).andExpect(status().isCreated())).get("id").asLong();
    }

    public long createMember() throws Exception {
        String body = """
                {"name": "Test Member", "email": "%s"}
                """.formatted(TestData.email());
        return read(postJson("/api/v1/members", body).andExpect(status().isCreated())).get("id").asLong();
    }

    public ResultActions borrow(long bookId, long memberId) throws Exception {
        return postJson("/api/v1/loans", """
                {"bookId": %d, "memberId": %d}
                """.formatted(bookId, memberId));
    }

    public long borrowOk(long bookId, long memberId) throws Exception {
        return read(borrow(bookId, memberId).andExpect(status().isCreated())).get("id").asLong();
    }

    public ResultActions returnLoan(long loanId) throws Exception {
        return mvc.perform(post("/api/v1/loans/{id}/return", loanId).with(LIBRARIAN));
    }

    public ResultActions get(String path, Object... vars) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path, vars).with(LIBRARIAN));
    }

    public JsonNode getJson(String path, Object... vars) throws Exception {
        return read(get(path, vars).andExpect(status().isOk()));
    }

    public ResultActions postJson(String path, String body) throws Exception {
        return mvc.perform(post(path).with(LIBRARIAN).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    public JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
