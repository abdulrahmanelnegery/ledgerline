package com.ledgerline.repo;

import com.ledgerline.domain.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    /** Account balance: the signed sum of every posted line, computed on read. */
    @Query("select coalesce(sum(l.amount), 0) from JournalLine l where l.account.id = :accountId")
    BigDecimal sumByAccountId(@Param("accountId") Long accountId);

    @Query("select l from JournalLine l join fetch l.entry where l.account.id = :accountId order by l.id asc")
    List<JournalLine> findByAccountIdOrderById(@Param("accountId") Long accountId);
}
