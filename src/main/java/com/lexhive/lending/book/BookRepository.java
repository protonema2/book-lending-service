package com.lexhive.lending.book;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, long id);

    /** {@code SELECT ... FOR UPDATE}: serializes catalog updates with concurrent borrows/returns of the same book. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id = :id")
    Optional<Book> findByIdForUpdate(long id);

    /**
     * Takes one copy off the shelf if any is left. The condition is evaluated by the database under the row lock
     * taken by the UPDATE itself, so concurrent borrowers of the last copy cannot both succeed.
     *
     * @return 1 if a copy was taken, 0 if none was available
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Book b
               set b.availableCopies = b.availableCopies - 1, b.updatedAt = :now
             where b.id = :id and b.availableCopies > 0
            """)
    int decrementAvailableCopies(long id, Instant now);

    /** @return 1 if the copy was put back, 0 if the book already has all copies (inconsistent state) */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Book b
               set b.availableCopies = b.availableCopies + 1, b.updatedAt = :now
             where b.id = :id and b.availableCopies < b.totalCopies
            """)
    int incrementAvailableCopies(long id, Instant now);
}
