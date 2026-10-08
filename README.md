# Book Lending Service

A Spring Boot service for managing a library's books, members, and loans.

The service supports book and member management, borrowing and returning books, configurable borrowing rules, role-based access, PostgreSQL persistence, API documentation, and basic application monitoring.

## Tech Stack

- Java 17
- Spring Boot 3.5
- PostgreSQL 16
- Flyway
- Spring Security
- Micrometer / Prometheus
- springdoc-openapi
- JUnit 5
- Testcontainers

## 1. Running the application

### Requirements

The easiest way to run the application is with Docker and Docker Compose.

No local Java or Maven installation is required when running the application through Docker.

```bash
docker compose up --build
```

Once the application starts:

| Resource | URL |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |
| Health | http://localhost:8080/actuator/health |

If port `8080` is already in use:

```bash
APP_PORT=8081 docker compose up --build
```

The PostgreSQL port can also be changed with `DB_PORT`.

### Demo users

The application comes with a few users for testing. Passwords are stored as BCrypt hashes in `application.yml`.

| Username | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `librarian` | `librarian123` | LIBRARIAN |
| `alice@example.com` | `alice123` | MEMBER |
| `bob@example.com` | `bob123` | MEMBER |

The database is seeded with sample books and members.

For Swagger, click **Authorize** and use one of the demo accounts above.

### Running without the application container

If you prefer to run the Spring Boot application directly:

```bash
docker compose up -d db
./mvnw spring-boot:run
```

The local profile is used when running this way.

## 2. Tests

Run the full test suite with:

```bash
./mvnw verify
```

Integration tests use Testcontainers, so Docker needs to be running.

The test suite covers several areas:

| Test type | Examples | Purpose |
|---|---|---|
| Unit | `LendingPolicyTest`, `LoanServiceTest` | Loan rules and service behaviour |
| Web | `BookControllerTest`, `LoanControllerTest` | Request validation, responses and access rules |
| Security | `SecurityRulesTest` | Authentication, authorization and member ownership |
| Persistence | `LoanRepositoryIT` | Database constraints and loan queries |
| Integration | `LoanFlowIT`, `CatalogIT` | End-to-end borrowing and returning flows |
| Concurrency | `ConcurrentBorrowIT` | Concurrent borrowing and stock handling |

The JaCoCo report is generated at:

```text
target/site/jacoco/index.html
```

The build requires at least 80% line coverage for the `loan` and `book` packages.

## 3. Configuration

Borrowing rules are configured in `application.yml` rather than stored in the database.

| Property | Environment variable | Default | Description |
|---|---|---:|---|
| `library.loan.max-active-loans` | `LIBRARY_LOAN_MAX_ACTIVE_LOANS` | `3` | Maximum number of unreturned loans per member |
| `library.loan.duration` | `LIBRARY_LOAN_DURATION` | `14d` | Loan duration |

For example:

```bash
LIBRARY_LOAN_MAX_ACTIVE_LOANS=5 \
LIBRARY_LOAN_DURATION=21d \
docker compose up --build
```

Invalid configuration values, such as a non-positive loan limit or duration, prevent the application from starting.

Application users are also configured in `application.yml`.

For a `MEMBER` user, the username must match the member's email address. This is used when checking whether a member is allowed to access their own data.

## 4. API

The API uses the `/api/v1` base path and returns JSON responses.

List endpoints support pagination:

```text
?page=0&size=20&sort=title,asc
```

The maximum page size is 100.

The complete API is available through Swagger UI. Example requests are also included in `api.http`.

### Books

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/books?title=&author=` | Any authenticated user | Search books |
| GET | `/books/{id}` | Any authenticated user | Get a book |
| POST | `/books` | LIBRARIAN, ADMIN | Add a book |
| PUT | `/books/{id}` | LIBRARIAN, ADMIN | Update a book |
| DELETE | `/books/{id}` | LIBRARIAN, ADMIN | Delete a book |

ISBN values are validated and stored without hyphens.

A book cannot be deleted when it has loan history.

### Members

| Method | Endpoint | Access | Description |
|---|---|---|---|
| GET | `/members` | LIBRARIAN, ADMIN | List members |
| GET | `/members/{id}` | LIBRARIAN, ADMIN, member | Get a member |
| POST | `/members` | LIBRARIAN, ADMIN | Create a member |
| PUT | `/members/{id}` | LIBRARIAN, ADMIN | Update a member |
| DELETE | `/members/{id}` | LIBRARIAN, ADMIN | Delete a member |
| GET | `/members/{id}/loans` | LIBRARIAN, ADMIN, member | Get a member's loans |

Member email addresses must be unique.

### Loans

| Method | Endpoint | Access | Description |
|---|---|---|---|
| POST | `/loans` | LIBRARIAN, ADMIN, member | Borrow a book |
| POST | `/loans/{id}/return` | LIBRARIAN, ADMIN, borrower | Return a book |
| GET | `/loans/{id}` | LIBRARIAN, ADMIN, borrower | Get a loan |
| GET | `/loans?status=&memberId=` | LIBRARIAN, ADMIN | Search loans |

A member can only access their own loans.

Loan status is calculated when the loan is read:

- `RETURNED` - the book has been returned
- `OVERDUE` - the book has not been returned and the due date has passed
- `ACTIVE` - the book has not been returned and is still within the loan period

## Borrowing rules

When a member borrows a book, the checks are performed in a fixed order:

| Order | Check | Error |
|---:|---|---|
| 1 | Member and book exist | `MEMBER_NOT_FOUND` / `BOOK_NOT_FOUND` |
| 2 | Member has no overdue loan | `HAS_OVERDUE_LOANS` |
| 3 | Member has not reached the active loan limit | `LOAN_LIMIT_EXCEEDED` |
| 4 | Member does not already have the same book | `DUPLICATE_ACTIVE_LOAN` |
| 5 | A copy is available | `BOOK_UNAVAILABLE` |

The fixed order makes the response predictable when more than one condition is invalid.

Trying to return an already returned loan results in:

```text
LOAN_ALREADY_RETURNED
```

## Errors

API errors use RFC 9457 Problem Details.

For example:

```json
{
  "type": "https://lexhive.example/errors/loan-limit-exceeded",
  "title": "Loan limit exceeded",
  "status": 409,
  "detail": "Member 7 already has 3 active loans (max 3).",
  "instance": "/api/v1/loans",
  "code": "LOAN_LIMIT_EXCEEDED",
  "traceId": "4f1c2a7e-9b1d-4c55-8a0e-2f9d1b7c3e10"
}
```

Other application error codes include:

- `VALIDATION_FAILED`
- `UNAUTHORIZED`
- `ACCESS_DENIED`
- `DUPLICATE_ISBN`
- `DUPLICATE_EMAIL`
- `COPIES_ON_LOAN`
- `ACTIVE_LOANS_EXIST`
- `LOAN_HISTORY_EXISTS`
- `INVALID_SORT_PROPERTY`
- `*_NOT_FOUND`
- `INTERNAL_ERROR`

## 5. Database

Flyway manages the database schema. Hibernate is configured to validate the schema rather than create or modify it.

The main tables are:

```text
BOOK
 └── LOAN
       └── MEMBER
```

### Book

- `id`
- `isbn`
- `title`
- `author`
- `total_copies`
- `available_copies`

### Member

- `id`
- `email`
- `name`

### Loan

- `id`
- `book_id`
- `member_id`
- `borrowed_at`
- `due_date`
- `returned_at`

The database also enforces several rules:

- Available copies cannot be negative or greater than total copies.
- A due date must be after the borrowing time.
- A return time cannot be before the borrowing time.
- ISBN and member email are unique.
- A member can have only one active loan for the same book.
- Active loan lookups have a partial index for the member and due date.

The partial indexes are useful here because most loan-related checks only care about loans that have not been returned.

## 6. Handling concurrent borrowing

Concurrency is one of the more important parts of the implementation.

For a borrow request, the member row is locked while the application checks the member's current loans and creates the new loan. This prevents two requests from both passing the loan-limit check at the same time.

Book stock is updated using a conditional SQL update:

```sql
UPDATE book
SET available_copies = available_copies - 1
WHERE id = ?
  AND available_copies > 0
```

If no row is updated, there is no copy available and the request is rejected.

This also handles the case where multiple requests try to borrow the last available copy.

The tests include concurrent requests to verify that:

- A member cannot exceed the configured loan limit.
- Two requests cannot borrow the same last copy.
- A loan cannot be returned twice.

The lock order is kept consistent between the relevant operations to reduce the chance of database deadlocks.

## 7. Security

The API currently uses HTTP Basic authentication with users configured in `application.yml`.

The application is stateless, so there is no server-side session.

| Operation | ADMIN | LIBRARIAN | MEMBER |
|---|:---:|:---:|:---:|
| Read books | ✓ | ✓ | ✓ |
| Manage books | ✓ | ✓ | — |
| Manage members | ✓ | ✓ | — |
| Read members | ✓ | ✓ | Own |
| Borrow | ✓ | ✓ | Own |
| Return / read loan | ✓ | ✓ | Own |
| Search all loans | ✓ | ✓ | — |
| Actuator endpoints | ✓ | — | — |

Endpoint-level authorization is handled with `@PreAuthorize`.

For members, the `memberAccess` component compares the authenticated username with the member's email address before allowing access.

For staff-only operations, access is also restricted at the URL level.

HTTP Basic was chosen because it keeps the sample application simple and is sufficient for the current scope. For a production system, I would move authentication to an external identity provider using OIDC/OAuth2 with short-lived tokens.

## 8. Monitoring and logging

The application exposes health and metrics endpoints through Spring Boot Actuator.

| Endpoint | Access | Purpose |
|---|---|---|
| `/actuator/health` | Public | Application and database health |
| `/health/liveness` | Public | Liveness check |
| `/health/readiness` | Public | Readiness check including database |
| `/actuator/info` | ADMIN | Build and runtime information |
| `/actuator/prometheus` | ADMIN | Prometheus metrics |
| `/actuator/metrics` | ADMIN | Metric information |

Business metrics include:

- `library_loans_borrowed_total`
- `library_loans_returned_total`
- `library_loans_rejected_total`
- `library_loans_active`
- `library_loans_overdue`

HTTP request latency and JVM/HikariCP metrics are also exposed through Micrometer.

Each request gets a correlation ID. If a valid `X-Request-Id` is supplied, it is reused; otherwise the application generates one.

The ID is returned in the response and included in application logs and error responses.

In Docker, logs are written as structured JSON. The local profile uses human-readable logs.

## 9. Project structure

The code is organised by feature rather than putting all controllers, services, and repositories into separate top-level packages.

```text
src/main/java/com/lexhive/lending
├── book/
│   ├── Book
│   ├── BookRepository
│   ├── BookService
│   ├── BookController
│   └── DTOs
├── member/
│   ├── Member
│   ├── MemberRepository
│   ├── MemberService
│   └── MemberController
├── loan/
│   ├── LendingPolicy
│   ├── LoanService
│   ├── LoanMetrics
│   └── LoanController
├── security/
│   ├── MemberAccess
│   ├── SecurityConfig
│   └── authentication / authorization handlers
├── config/
│   ├── LoanPolicyProperties
│   ├── SecurityConfig
│   ├── OpenAPI configuration
│   └── Clock configuration
└── common/
    ├── exceptions
    ├── ProblemDetail handling
    ├── correlation ID filter
    └── pagination
```

Keeping the code grouped by feature makes it easier to find the code related to a particular part of the system.

## 10. Design decisions

### PostgreSQL instead of H2

I used PostgreSQL for both the application and integration tests.

The application relies on PostgreSQL features such as partial indexes and row-level locking. Using Testcontainers means the integration tests run against the same database technology instead of relying on H2 compatibility.

### Database constraints

Some rules are checked in Java, but important data constraints are also enforced by PostgreSQL.

For example, the database prevents invalid copy counts and duplicate active loans even if a bug in the application allows an invalid request through.

This gives the application a second layer of protection.

### Injected Clock

The loan service uses an injected `Clock` instead of calling `Instant.now()` directly.

This makes it possible to move time forward in tests and verify overdue behaviour without waiting for the actual due date.

### No JPA relationships for loans

Loans store `book_id` and `member_id` rather than using JPA entity relationships.

For this service, a loan only needs to reference the two IDs. This avoids unnecessary lazy loading and keeps the loan aggregate relatively small.

### Package by feature

The project is organised around `book`, `member`, and `loan` rather than technical layers.

This keeps the code for a particular feature together and makes the structure easier to navigate as the application grows.

### Authentication

HTTP Basic with configuration-based users keeps the current application simple.

If this service were connected to a larger platform, I would move user management and authentication outside the service and use OIDC/OAuth2.
