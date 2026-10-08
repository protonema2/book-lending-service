package com.lexhive.lending.loan;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class LoanSpecifications {

    private static final String RETURNED_AT = "returnedAt";
    private static final String DUE_DATE = "dueDate";

    private LoanSpecifications() {
    }

    /** Optional filters; {@code null} criteria are ignored. */
    static Specification<Loan> matching(LoanStatus status, Long memberId, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(hasStatus(status, now).toPredicate(root, query, cb));
            }
            if (memberId != null) {
                predicates.add(cb.equal(root.get("memberId"), memberId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Mirrors {@link Loan#statusAt(Instant)}: the filter returns exactly the loans showing that status. */
    static Specification<Loan> hasStatus(LoanStatus status, Instant now) {
        return switch (status) {
            case ACTIVE -> (root, query, cb) -> cb.and(
                    cb.isNull(root.get(RETURNED_AT)),
                    cb.greaterThanOrEqualTo(root.<Instant>get(DUE_DATE), now));
            case OVERDUE -> (root, query, cb) -> cb.and(
                    cb.isNull(root.get(RETURNED_AT)),
                    cb.lessThan(root.<Instant>get(DUE_DATE), now));
            case RETURNED -> (root, query, cb) -> cb.isNotNull(root.get(RETURNED_AT));
        };
    }
}
