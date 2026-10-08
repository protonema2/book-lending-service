package com.lexhive.lending.book;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

final class BookSpecifications {

    private static final char ESCAPE = '\\';

    private BookSpecifications() {
    }

    /** Case-insensitive "contains" filter on title and/or author; blank criteria are ignored. */
    static Specification<Book> matching(String title, String author) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(title)) {
                predicates.add(containsIgnoreCase(cb, root.get("title"), title));
            }
            if (StringUtils.hasText(author)) {
                predicates.add(containsIgnoreCase(cb, root.get("author"), author));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Predicate containsIgnoreCase(CriteriaBuilder cb, Expression<String> field, String term) {
        String escaped = term.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return cb.like(cb.lower(field), "%" + escaped + "%", ESCAPE);
    }
}
