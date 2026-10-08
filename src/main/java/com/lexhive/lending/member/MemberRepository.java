package com.lexhive.lending.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, long id);

    boolean existsByIdAndEmail(long id, String email);

    /**
     * {@code SELECT ... FOR UPDATE}: serializes borrows of the same member so two concurrent requests
     * cannot both pass the active-loan limit check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(long id);
}
