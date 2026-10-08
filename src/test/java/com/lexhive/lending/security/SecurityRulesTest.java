package com.lexhive.lending.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexhive.lending.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Role and ownership rules against the real security configuration (YAML users, BCrypt, HTTP Basic). */
@IntegrationTest
@AutoConfigureObservability // Spring Boot tests disable metric exporters by default
class SecurityRulesTest {

    private static final RequestPostProcessor ADMIN = httpBasic("admin", "admin123");
    private static final RequestPostProcessor LIBRARIAN = httpBasic("librarian", "librarian123");
    private static final RequestPostProcessor ALICE = httpBasic("alice@example.com", "alice123");
    private static final RequestPostProcessor BOB = httpBasic("bob@example.com", "bob123");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private long aliceId;
    private long bobId;

    @BeforeEach
    void lookUpSeedMembers() {
        aliceId = memberId("alice@example.com");
        bobId = memberId("bob@example.com");
    }

    @Test
    void anonymousRequestsAreRejectedWithBasicChallenge() throws Exception {
        mvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"book-lending\""))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(get("/api/v1/books").with(httpBasic("librarian", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @CsvSource({
            "GET,  /api/v1/books,   alice,     200",
            "GET,  /api/v1/members, alice,     403",
            "GET,  /api/v1/members, librarian, 200",
            "GET,  /api/v1/members, admin,     200",
            "GET,  /api/v1/loans,   alice,     403",
            "GET,  /api/v1/loans,   librarian, 200",
            "POST, /api/v1/books,   alice,     403",
    })
    void roleMatrix(String method, String path, String user, int expectedStatus) throws Exception {
        var request = "POST".equals(method)
                ? post(path).contentType(MediaType.APPLICATION_JSON).content("{}")
                : get(path);
        mvc.perform(request.with(credentials(user)))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void memberCanReadOnlyOwnProfileAndLoans() throws Exception {
        mvc.perform(get("/api/v1/members/{id}", aliceId).with(ALICE)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/members/{id}/loans", aliceId).with(ALICE)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/{id}", bobId).with(ALICE)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/members/{id}/loans", bobId).with(ALICE)).andExpect(status().isForbidden());
    }

    @Test
    void memberCanBorrowAndReturnOnlyForThemself() throws Exception {
        long bookId = jdbc.queryForObject("select id from book where isbn = '9781617294945'", Long.class);

        mvc.perform(post("/api/v1/loans").with(ALICE).contentType(MediaType.APPLICATION_JSON)
                        .content(borrowBody(bookId, bobId)))
                .andExpect(status().isForbidden());

        String created = mvc.perform(post("/api/v1/loans").with(ALICE).contentType(MediaType.APPLICATION_JSON)
                        .content(borrowBody(bookId, aliceId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long loanId = json.readTree(created).get("id").asLong();

        mvc.perform(get("/api/v1/loans/{id}", loanId).with(BOB)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/{id}/return", loanId).with(BOB)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/loans/{id}/return", loanId).with(ALICE)).andExpect(status().isOk());
    }

    @Test
    void staffCanBorrowOnBehalfOfAnyMember() throws Exception {
        long bookId = jdbc.queryForObject("select id from book where isbn = '9780596007126'", Long.class);

        String created = mvc.perform(post("/api/v1/loans").with(LIBRARIAN).contentType(MediaType.APPLICATION_JSON)
                        .content(borrowBody(bookId, bobId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/loans/{id}/return", json.readTree(created).get("id").asLong()).with(ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorIsAdminOnlyExceptHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/prometheus").with(LIBRARIAN)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/prometheus").with(ADMIN))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("library_loans_active")))
                .andExpect(content().string(containsString("library_loans_overdue")));
        mvc.perform(get("/actuator/info").with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void unmappedPathsAreDenied() throws Exception {
        mvc.perform(get("/internal/anything").with(ADMIN)).andExpect(status().isForbidden());
    }

    private long memberId(String email) {
        return jdbc.queryForObject("select id from member where email = ?", Long.class, email);
    }

    private static String borrowBody(long bookId, long memberId) {
        return """
                {"bookId": %d, "memberId": %d}
                """.formatted(bookId, memberId);
    }

    private static RequestPostProcessor credentials(String user) {
        return switch (user) {
            case "admin" -> ADMIN;
            case "librarian" -> LIBRARIAN;
            case "alice" -> ALICE;
            case "bob" -> BOB;
            default -> throw new IllegalArgumentException(user);
        };
    }
}
