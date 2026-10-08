-- Book catalog. available_copies is maintained by atomic UPDATEs on borrow/return.
CREATE TABLE book (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    author           VARCHAR(255) NOT NULL,
    isbn             VARCHAR(17)  NOT NULL,
    total_copies     INT          NOT NULL,
    available_copies INT          NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_book_isbn UNIQUE (isbn),
    CONSTRAINT chk_total_copies_non_negative CHECK (total_copies >= 0),
    CONSTRAINT chk_available_range CHECK (available_copies BETWEEN 0 AND total_copies)
);

CREATE TABLE member (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_member_email UNIQUE (email)
);

CREATE TABLE loan (
    id          BIGSERIAL PRIMARY KEY,
    book_id     BIGINT      NOT NULL REFERENCES book (id),
    member_id   BIGINT      NOT NULL REFERENCES member (id),
    borrowed_at TIMESTAMPTZ NOT NULL,
    due_date    TIMESTAMPTZ NOT NULL,
    returned_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_due_after_borrow CHECK (due_date > borrowed_at),
    CONSTRAINT chk_return_after_borrow CHECK (returned_at IS NULL OR returned_at >= borrowed_at)
);

-- Loan history per member (GET /members/{id}/loans)
CREATE INDEX idx_loan_member ON loan (member_id);
-- Fast active/overdue checks during borrow (partial index: only open loans)
CREATE INDEX idx_loan_member_active ON loan (member_id, due_date) WHERE returned_at IS NULL;
CREATE INDEX idx_loan_book ON loan (book_id);

-- DB-level guarantee: at most one active loan per (member, book)
CREATE UNIQUE INDEX uq_loan_active_member_book ON loan (member_id, book_id) WHERE returned_at IS NULL;
