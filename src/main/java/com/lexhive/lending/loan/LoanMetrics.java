package com.lexhive.lending.loan;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Business metrics, exported at {@code /actuator/prometheus} as {@code library_loans_*}.
 * Gauges are computed from the database on each scrape, so they are correct across multiple instances.
 */
@Component
public class LoanMetrics {

    private final MeterRegistry registry;
    private final Counter borrowed;
    private final Counter returned;

    public LoanMetrics(MeterRegistry registry, LoanRepository loans, Clock clock) {
        this.registry = registry;
        this.borrowed = Counter.builder("library.loans.borrowed")
                .description("Successful borrows")
                .register(registry);
        this.returned = Counter.builder("library.loans.returned")
                .description("Successful returns")
                .register(registry);
        Gauge.builder("library.loans.active", loans, LoanRepository::countActive)
                .description("Loans not yet returned (overdue included)")
                .register(registry);
        Gauge.builder("library.loans.overdue", loans, repo -> repo.countOverdue(Instant.now(clock)))
                .description("Loans not returned and past their due date")
                .register(registry);
    }

    public void borrowed() {
        borrowed.increment();
    }

    public void returned() {
        returned.increment();
    }

    /** @param reason the rejection's error code, e.g. {@code LOAN_LIMIT_EXCEEDED} */
    public void rejected(String reason) {
        Counter.builder("library.loans.rejected")
                .description("Borrow/return requests rejected by a lending rule")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }
}
