package com.lexhive.lending.common.error;

import com.lexhive.lending.common.logging.CorrelationIdFilter;
import java.net.URI;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details with the service-specific {@code code} and {@code traceId} extensions. */
public final class ProblemDetails {

    public static final String CODE = "code";
    public static final String TRACE_ID = "traceId";
    private static final String TYPE_BASE = "https://lexhive.example/errors/";

    private ProblemDetails() {
    }

    /** {@code LOAN_LIMIT_EXCEEDED} becomes type {@code .../loan-limit-exceeded} and title {@code Loan limit exceeded}. */
    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        String words = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        problem.setType(URI.create(TYPE_BASE + words.replace(' ', '-')));
        problem.setTitle(Character.toUpperCase(words.charAt(0)) + words.substring(1));
        problem.setProperty(CODE, code);
        return withTraceId(problem);
    }

    /** Adds a code (derived from the HTTP status when absent) and the current trace id. */
    public static ProblemDetail enrich(ProblemDetail problem) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
            HttpStatus status = HttpStatus.resolve(problem.getStatus());
            problem.setProperty(CODE, status != null ? status.name() : "ERROR");
        }
        return withTraceId(problem);
    }

    private static ProblemDetail withTraceId(ProblemDetail problem) {
        String traceId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty(TRACE_ID, traceId);
        }
        return problem;
    }
}
