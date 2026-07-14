package com.trademaster.ims.repository;

import com.trademaster.ims.model.FinancialAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, Long> {
    boolean existsByAccountCode(String accountCode);
    Page<FinancialAccount> findByStatus(FinancialAccount.AccountStatus status, Pageable pageable);
    Page<FinancialAccount> findByAccountType(FinancialAccount.AccountType accountType, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM FinancialAccount a WHERE a.accountId = :id")
    Optional<FinancialAccount> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT a FROM FinancialAccount a WHERE LOWER(a.accountName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(a.accountCode) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<FinancialAccount> search(@Param("keyword") String keyword, Pageable pageable);
}
