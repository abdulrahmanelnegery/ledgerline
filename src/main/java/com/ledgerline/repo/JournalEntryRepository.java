package com.ledgerline.repo;

import com.ledgerline.domain.JournalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @EntityGraph(attributePaths = {"lines", "lines.account"})
    Optional<JournalEntry> findWithLinesById(Long id);
}
