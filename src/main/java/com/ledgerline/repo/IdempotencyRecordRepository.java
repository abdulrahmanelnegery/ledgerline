package com.ledgerline.repo;

import com.ledgerline.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {

    /**
     * Claim an idempotency key by inserting its row in the same transaction that
     * writes the entry. The primary key on {@code idem_key} makes this fail for
     * a second concurrent poster using the same key; that transaction then rolls
     * back and the caller returns the winner's entry. Runs as immediate SQL so
     * the constraint violation surfaces here, not at a later flush.
     */
    @Modifying
    @Query(value = "insert into idempotency_key (idem_key, journal_entry_id, created_at) "
            + "values (:key, :entryId, current_timestamp)", nativeQuery = true)
    void insertRecord(@Param("key") String key, @Param("entryId") Long entryId);
}
