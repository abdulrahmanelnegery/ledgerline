package com.ledgerline.repo;

import com.ledgerline.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, String> {
}
