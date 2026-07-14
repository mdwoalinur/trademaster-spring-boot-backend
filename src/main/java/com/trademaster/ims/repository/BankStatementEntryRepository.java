package com.trademaster.ims.repository;

import com.trademaster.ims.model.BankStatementEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface BankStatementEntryRepository extends JpaRepository<BankStatementEntry, Long> {
    Page<BankStatementEntry> findAllByOrderByTransactionDateDesc(Pageable pageable);
    Page<BankStatementEntry> findByStatusOrderByTransactionDateDesc(BankStatementEntry.ReconciliationStatus status, Pageable pageable);
    Page<BankStatementEntry> findByAccountIdOrderByTransactionDateDesc(Long accountId, Pageable pageable);
    Page<BankStatementEntry> findByAccountIdAndStatusOrderByTransactionDateDesc(Long accountId, BankStatementEntry.ReconciliationStatus status, Pageable pageable);
    Optional<BankStatementEntry> findFirstByAccountIdAndStatementReferenceAndTransactionDate(Long accountId, String statementReference, LocalDate transactionDate);
    long countByStatus(BankStatementEntry.ReconciliationStatus status);
}
