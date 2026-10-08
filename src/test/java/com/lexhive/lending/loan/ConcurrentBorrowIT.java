package com.lexhive.lending.loan;

import static org.assertj.core.api.Assertions.assertThat;

import com.lexhive.lending.book.BookService;
import com.lexhive.lending.book.dto.BookRequest;
import com.lexhive.lending.common.error.BookUnavailableException;
import com.lexhive.lending.common.error.LoanAlreadyReturnedException;
import com.lexhive.lending.common.error.LoanLimitExceededException;
import com.lexhive.lending.member.MemberService;
import com.lexhive.lending.member.dto.MemberRequest;
import com.lexhive.lending.support.IntegrationTest;
import com.lexhive.lending.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Races against real PostgreSQL row locks. All threads are released at the same instant by a latch, so the
 * requests genuinely overlap inside their transactions.
 */
@IntegrationTest
class ConcurrentBorrowIT {

    private static final int THREADS = 10;

    @Autowired
    private LoanService loanService;
    @Autowired
    private BookService bookService;
    @Autowired
    private MemberService memberService;
    @Autowired
    private JdbcTemplate jdbc;

    private final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    @AfterEach
    void shutDown() {
        executor.shutdownNow();
    }

    @Test
    void lastCopyIsLentExactlyOnce() throws Exception {
        long book = newBook(1);
        List<Long> borrowers = IntStream.range(0, THREADS).mapToObj(i -> newMember()).toList();

        var outcomes = race(borrowers.stream()
                .<Callable<Object>>map(member -> () -> loanService.borrow(book, member))
                .toList());

        assertThat(successes(outcomes)).isEqualTo(1);
        assertThat(failures(outcomes, BookUnavailableException.class)).isEqualTo(THREADS - 1);
        assertThat(availableCopies(book)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan where book_id = ?", Long.class, book)).isEqualTo(1);
    }

    @Test
    void memberCannotExceedLimitWithParallelRequests() throws Exception {
        long member = newMember();
        List<Long> books = IntStream.range(0, 6).mapToObj(i -> newBook(1)).toList();

        var outcomes = race(books.stream()
                .<Callable<Object>>map(book -> () -> loanService.borrow(book, member))
                .toList());

        assertThat(successes(outcomes)).isEqualTo(3);
        assertThat(failures(outcomes, LoanLimitExceededException.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from loan where member_id = ? and returned_at is null",
                Long.class, member)).isEqualTo(3);
    }

    @Test
    void loanIsReturnedExactlyOnce() throws Exception {
        long book = newBook(1);
        long loan = loanService.borrow(book, newMember()).id();

        var outcomes = race(IntStream.range(0, 5)
                .<Callable<Object>>mapToObj(i -> () -> loanService.returnLoan(loan))
                .toList());

        assertThat(successes(outcomes)).isEqualTo(1);
        assertThat(failures(outcomes, LoanAlreadyReturnedException.class)).isEqualTo(4);
        assertThat(availableCopies(book)).isEqualTo(1);
    }

    /** Starts all tasks at once and returns each result or the exception it threw. */
    private List<Object> race(List<Callable<Object>> tasks) throws Exception {
        var ready = new CountDownLatch(tasks.size());
        var start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get(30, TimeUnit.SECONDS));
        }
        return outcomes;
    }

    private static long successes(List<Object> outcomes) {
        return outcomes.stream().filter(o -> !(o instanceof Exception)).count();
    }

    private static long failures(List<Object> outcomes, Class<? extends Exception> type) {
        return outcomes.stream().filter(type::isInstance).count();
    }

    private long newBook(int copies) {
        return bookService.create(new BookRequest("Race Book", "Author", TestData.isbn13(), copies)).id();
    }

    private long newMember() {
        return memberService.create(new MemberRequest("Racer", TestData.email())).id();
    }

    private int availableCopies(long book) {
        return jdbc.queryForObject("select available_copies from book where id = ?", Integer.class, book);
    }
}
