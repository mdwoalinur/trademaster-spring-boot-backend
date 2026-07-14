package com.trademaster.ims.repository;

import com.trademaster.ims.model.AccountLedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AccountLedgerEntryRepository extends JpaRepository<AccountLedgerEntry, Long> {
    Page<AccountLedgerEntry> findByAccountIdOrderByPostedAtDesc(Long accountId, Pageable pageable);
    Page<AccountLedgerEntry> findByAccountIdAndPostedAtBetweenOrderByPostedAtDesc(Long accountId, LocalDateTime start, LocalDateTime end, Pageable pageable);
    boolean existsByAccountId(Long accountId);
}
