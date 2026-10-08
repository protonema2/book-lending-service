package com.lexhive.lending.loan;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    @Query("""
            select count(l) from Loan l
             where l.memberId = :memberId and l.returnedAt is null
            """)
    long countActiveByMember(long memberId);

    @Query("""
            select count(l) > 0 from Loan l
             where l.memberId = :memberId and l.returnedAt is null and l.dueDate < :now
            """)
    boolean existsOverdueByMember(long memberId, Instant now);

    @Query("""
            select count(l) > 0 from Loan l
             where l.memberId = :memberId and l.bookId = :bookId and l.returnedAt is null
            """)
    boolean existsActiveByMemberAndBook(long memberId, long bookId);

    boolean existsByBookIdAndReturnedAtIsNull(long bookId);

    boolean existsByBookId(long bookId);

    boolean existsByMemberIdAndReturnedAtIsNull(long memberId);

    boolean existsByMemberId(long memberId);

    Page<Loan> findByMemberId(long memberId, Pageable pageable);

    /** Serializes concurrent returns of the same loan (prevents double-incrementing the book stock). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findByIdForUpdate(long id);

    /** Ownership check for MEMBER users: does the loan belong to the member with this email? */
    @Query("""
            select count(l) > 0 from Loan l join Member m on m.id = l.memberId
             where l.id = :loanId and m.email = :email
            """)
    boolean isOwnedByMemberEmail(long loanId, String email);

    @Query("select count(l) from Loan l where l.returnedAt is null")
    long countActive();

    @Query("select count(l) from Loan l where l.returnedAt is null and l.dueDate < :now")
    long countOverdue(Instant now);
}
