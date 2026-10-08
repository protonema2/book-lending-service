package com.lexhive.lending.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata, HTTP Basic security scheme and the shared RFC 9457 error responses. */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
        info = @Info(
                title = "Book Lending Service",
                version = "v1",
                description = """
                        Manage books, members and loans. Borrowing rules (max active loans, loan duration) come from
                        configuration. Errors are RFC 9457 problem details with a stable `code` and a `traceId`
                        (also returned as the `X-Request-Id` header).

                        Demo users: `admin` / `admin123`, `librarian` / `librarian123`,
                        `alice@example.com` / `alice123`, `bob@example.com` / `bob123`."""),
        security = @SecurityRequirement(name = OpenApiConfig.BASIC_AUTH))
@SecurityScheme(name = OpenApiConfig.BASIC_AUTH, type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {

    static final String BASIC_AUTH = "basicAuth";
    private static final String PROBLEM_SCHEMA = "Problem";
    private static final String PROBLEM_JSON = "application/problem+json";

    /**
     * Documents the error responses every operation can produce, so clients see the problem format without
     * repeating {@code @ApiResponse} annotations on each endpoint.
     */
    @Bean
    OpenApiCustomizer problemResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_SCHEMA, problemSchema());
            openApi.getPaths().values().forEach(path -> path.readOperationsMap().forEach((method, operation) -> {
                add(operation, "401", "Missing or invalid credentials", "UNAUTHORIZED");
                add(operation, "403", "Authenticated but not allowed (role or ownership)", "ACCESS_DENIED");
                if (operation.getRequestBody() != null || hasParameters(operation)) {
                    add(operation, "400", "Invalid request (validation, malformed JSON, bad parameter)", "VALIDATION_FAILED");
                }
                if (path.getParameters() != null || hasPathParameter(operation)) {
                    add(operation, "404", "Resource not found", "BOOK_NOT_FOUND");
                }
                if (method != PathItem.HttpMethod.GET) {
                    add(operation, "409", "Business rule violated", "LOAN_LIMIT_EXCEEDED");
                }
            }));
        };
    }

    private static void add(Operation operation, String status, String description, String exampleCode) {
        if (operation.getResponses().containsKey(status)) {
            return;
        }
        var example = new Example().value(Map.of(
                "type", "https://lexhive.example/errors/" + exampleCode.toLowerCase().replace('_', '-'),
                "title", exampleCode.charAt(0) + exampleCode.substring(1).toLowerCase().replace('_', ' '),
                "status", Integer.parseInt(status),
                "detail", "Human-readable explanation.",
                "code", exampleCode,
                "traceId", "4f1c2a7e-9b1d-4c55-8a0e-2f9d1b7c3e10"));
        var media = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_SCHEMA))
                .addExamples(exampleCode, example);
        operation.getResponses().addApiResponse(status,
                new ApiResponse().description(description).content(new Content().addMediaType(PROBLEM_JSON, media)));
    }

    private static boolean hasParameters(Operation operation) {
        return operation.getParameters() != null && !operation.getParameters().isEmpty();
    }

    private static boolean hasPathParameter(Operation operation) {
        return hasParameters(operation) && operation.getParameters().stream().anyMatch(p -> "path".equals(p.getIn()));
    }

    private static Schema<?> problemSchema() {
        var fieldError = new ObjectSchema()
                .addProperty("field", new StringSchema())
                .addProperty("message", new StringSchema());
        return new ObjectSchema()
                .description("RFC 9457 problem detail")
                .addProperty("type", new StringSchema().format("uri"))
                .addProperty("title", new StringSchema())
                .addProperty("status", new IntegerSchema())
                .addProperty("detail", new StringSchema())
                .addProperty("instance", new StringSchema().format("uri"))
                .addProperty("code", new StringSchema().description("Stable machine-readable error code"))
                .addProperty("traceId", new StringSchema().description("Correlation id, same as the X-Request-Id header"))
                .addProperty("errors", new ArraySchema().items(fieldError).description("Field errors (validation only)"));
    }
}
