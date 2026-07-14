package com.trademaster.ims.service;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.model.AccountLedgerEntry;
import com.trademaster.ims.model.FinancialAccount;
import com.trademaster.ims.repository.AccountLedgerEntryRepository;
import com.trademaster.ims.repository.FinancialAccountRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class FinancialAccountService {

    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private AccountLedgerEntryRepository ledgerRepository;
    @Autowired private AuthContextService authContextService;

    public Page<FinancialAccount> getAccounts(Pageable pageable, String type, String status, String search) {
        if (search != null && !search.isBlank()) {
            return accountRepository.search(search, pageable);
        }
        if (type != null && !type.isBlank()) {
            return accountRepository.findByAccountType(FinancialAccount.AccountType.valueOf(type), pageable);
        }
        if (status != null && !status.isBlank()) {
            return accountRepository.findByStatus(FinancialAccount.AccountStatus.valueOf(status), pageable);
        }
        return accountRepository.findAll(pageable);
    }

    public FinancialAccount getAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "FinancialAccount")
    public FinancialAccount createAccount(FinancialAccount account) {
        if (accountRepository.existsByAccountCode(account.getAccountCode())) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Account code already exists");
        }
        account.setAccountId(null);
        account.setCompanyId(authContextService.getCurrentCompanyId());
        account.setCreatedBy(authContextService.getCurrentUserId());
        if (account.getCurrentBalance() == null || account.getCurrentBalance().compareTo(account.getOpeningBalance()) != 0) {
            account.setCurrentBalance(account.getOpeningBalance());
        }
        FinancialAccount saved = accountRepository.save(account);
        createOpeningLedger(saved);
        return saved;
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "FinancialAccount")
    public FinancialAccount updateAccount(Long id, FinancialAccount data) {
        FinancialAccount account = getAccount(id);
        account.setAccountName(data.getAccountName());
        account.setAccountType(data.getAccountType());
        account.setAccountNumber(data.getAccountNumber());
        account.setBankName(data.getBankName());
        account.setBranchName(data.getBranchName());
        account.setMobileProvider(data.getMobileProvider());
        account.setCurrencyCode(data.getCurrencyCode());
        account.setAllowOverdraft(data.getAllowOverdraft());
        account.setWarehouseId(data.getWarehouseId());
        return accountRepository.save(account);
    }

    @Transactional
    @Auditable(action = "STATUS_CHANGE", entityType = "FinancialAccount")
    public FinancialAccount updateStatus(Long id, FinancialAccount.AccountStatus status) {
        FinancialAccount account = getAccount(id);
        account.setStatus(status);
        return accountRepository.save(account);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "FinancialAccount")
    public void deleteAccount(Long id) {
        FinancialAccount account = getAccount(id);
        account.setStatus(FinancialAccount.AccountStatus.INACTIVE);
        accountRepository.save(account);
    }

    public Page<AccountLedgerEntry> getStatement(Long accountId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        getAccount(accountId);
        if (startDate != null && endDate != null) {
            return ledgerRepository.findByAccountIdAndPostedAtBetweenOrderByPostedAtDesc(
                    accountId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), pageable);
        }
        return ledgerRepository.findByAccountIdOrderByPostedAtDesc(accountId, pageable);
    }

    private void createOpeningLedger(FinancialAccount account) {
        if (account.getOpeningBalance() == null || account.getOpeningBalance().signum() == 0) {
            return;
        }
        AccountLedgerEntry entry = new AccountLedgerEntry();
        entry.setAccountId(account.getAccountId());
        entry.setEntryType(AccountLedgerEntry.EntryType.OPENING_BALANCE);
        entry.setDirection(com.trademaster.ims.model.Payment.PaymentDirection.RECEIVE);
        entry.setDebitAmount(account.getOpeningBalance());
        entry.setCreditAmount(java.math.BigDecimal.ZERO);
        entry.setBalanceBefore(java.math.BigDecimal.ZERO);
        entry.setBalanceAfter(account.getOpeningBalance());
        entry.setDescription("Opening balance");
        entry.setPostedBy(account.getCreatedBy());
        entry.setPostedAt(LocalDateTime.now());
        entry.setCompanyId(account.getCompanyId());
        ledgerRepository.save(entry);
    }
}
