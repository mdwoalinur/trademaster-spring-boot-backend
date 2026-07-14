package com.trademaster.ims.service;

import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.BankStatementEntry;
import com.trademaster.ims.model.FinancialAccount;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.repository.BankStatementEntryRepository;
import com.trademaster.ims.repository.FinancialAccountRepository;
import com.trademaster.ims.repository.PaymentRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PaymentReconciliationService {
    @Autowired private BankStatementEntryRepository statementRepository;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AuthContextService authContextService;

    public Page<BankStatementEntry> list(Long accountId, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            BankStatementEntry.ReconciliationStatus reconciliationStatus;
            try {
                reconciliationStatus = BankStatementEntry.ReconciliationStatus.valueOf(status);
            } catch (IllegalArgumentException ex) {
                throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Invalid reconciliation status: " + status);
            }
            if (accountId == null) {
                return statementRepository.findByStatusOrderByTransactionDateDesc(reconciliationStatus, pageable);
            }
            return statementRepository.findByAccountIdAndStatusOrderByTransactionDateDesc(
                    accountId, reconciliationStatus, pageable);
        }
        if (accountId == null) {
            return statementRepository.findAllByOrderByTransactionDateDesc(pageable);
        }
        return statementRepository.findByAccountIdOrderByTransactionDateDesc(accountId, pageable);
    }

    @Transactional
    @Auditable(action = "IMPORT", entityType = "BankStatementEntry")
    public Map<String, Object> importCsv(Long accountId, MultipartFile file) {
        FinancialAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Financial account not found"));
        if (account.getAccountType() != FinancialAccount.AccountType.BANK) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only BANK accounts can be reconciled");
        }
        String batchId = "BANK-" + accountId + "-" + System.currentTimeMillis();
        int imported = 0;
        int duplicates = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                if (first && line.toLowerCase(Locale.ROOT).contains("date")) {
                    first = false;
                    continue;
                }
                first = false;
                String[] p = line.split(",", -1);
                if (p.length < 5) continue;
                LocalDate txDate = LocalDate.parse(p[0].trim());
                String reference = p[1].trim();
                String description = p[2].trim();
                BigDecimal debit = parseAmount(p[3]);
                BigDecimal credit = parseAmount(p[4]);
                BigDecimal balance = p.length > 5 ? parseAmount(p[5]) : BigDecimal.ZERO;
                if (statementRepository.findFirstByAccountIdAndStatementReferenceAndTransactionDate(accountId, reference, txDate).isPresent()) {
                    duplicates++;
                    continue;
                }
                BankStatementEntry entry = new BankStatementEntry();
                entry.setAccountId(accountId);
                entry.setTransactionDate(txDate);
                entry.setValueDate(txDate);
                entry.setStatementReference(reference);
                entry.setDescription(description);
                entry.setDebitAmount(debit);
                entry.setCreditAmount(credit);
                entry.setExternalBalance(balance);
                entry.setImportedBy(authContextService.getCurrentUserId());
                entry.setImportBatchId(batchId);
                suggestMatch(entry);
                statementRepository.save(entry);
                imported++;
            }
        } catch (Exception ex) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Invalid bank statement CSV");
        }
        return Map.of("batchId", batchId, "imported", imported, "duplicates", duplicates);
    }

    @Transactional
    @Auditable(action = "MATCH", entityType = "BankStatementEntry")
    public BankStatementEntry match(Long statementEntryId, Long paymentId) {
        BankStatementEntry entry = statementRepository.findById(statementEntryId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Statement entry not found"));
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getTransactionStatus() != Payment.PaymentTransactionStatus.POSTED) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only posted payments can be reconciled");
        }
        if (payment.getAccountId() == null || !payment.getAccountId().equals(entry.getAccountId())) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Payment account does not match statement account");
        }
        entry.setMatchedPaymentId(paymentId);
        entry.setStatus(BankStatementEntry.ReconciliationStatus.MATCHED);
        return statementRepository.save(entry);
    }

    @Transactional
    @Auditable(action = "RECONCILE", entityType = "BankStatementEntry")
    public BankStatementEntry reconcile(Long statementEntryId, String comments) {
        BankStatementEntry entry = statementRepository.findById(statementEntryId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Statement entry not found"));
        if (entry.getMatchedPaymentId() == null) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Statement entry must be matched before reconciliation");
        }
        Payment payment = paymentRepository.findByIdForUpdate(entry.getMatchedPaymentId())
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Matched payment not found"));
        payment.setReconciliationStatus(Payment.ReconciliationStatus.RECONCILED);
        paymentRepository.save(payment);
        entry.setStatus(BankStatementEntry.ReconciliationStatus.RECONCILED);
        entry.setReconciledBy(authContextService.getCurrentUserId());
        entry.setReconciledAt(LocalDateTime.now());
        entry.setComments(comments);
        return statementRepository.save(entry);
    }

    @Transactional
    @Auditable(action = "UNMATCH", entityType = "BankStatementEntry")
    public BankStatementEntry unmatch(Long statementEntryId) {
        BankStatementEntry entry = statementRepository.findById(statementEntryId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Statement entry not found"));
        if (entry.getMatchedPaymentId() != null) {
            paymentRepository.findByIdForUpdate(entry.getMatchedPaymentId()).ifPresent(payment -> {
                payment.setReconciliationStatus(Payment.ReconciliationStatus.UNRECONCILED);
                paymentRepository.save(payment);
            });
        }
        entry.setMatchedPaymentId(null);
        entry.setStatus(BankStatementEntry.ReconciliationStatus.UNMATCHED);
        entry.setReconciledBy(null);
        entry.setReconciledAt(null);
        return statementRepository.save(entry);
    }

    public Map<String, Object> summary() {
        return Map.of(
                "unmatched", statementRepository.countByStatus(BankStatementEntry.ReconciliationStatus.UNMATCHED),
                "matched", statementRepository.countByStatus(BankStatementEntry.ReconciliationStatus.MATCHED),
                "reconciled", statementRepository.countByStatus(BankStatementEntry.ReconciliationStatus.RECONCILED),
                "mismatch", statementRepository.countByStatus(BankStatementEntry.ReconciliationStatus.MISMATCH)
        );
    }

    private void suggestMatch(BankStatementEntry entry) {
        BigDecimal statementAmount = entry.getCreditAmount() != null && entry.getCreditAmount().compareTo(BigDecimal.ZERO) > 0
                ? entry.getCreditAmount()
                : entry.getDebitAmount();
        Page<Payment> candidates = paymentRepository.findByPaymentDateBetween(
                entry.getTransactionDate().minusDays(3).atStartOfDay(),
                entry.getTransactionDate().plusDays(3).atTime(23, 59, 59),
                Pageable.unpaged());
        candidates.stream()
                .filter(p -> p.getTransactionStatus() == Payment.PaymentTransactionStatus.POSTED)
                .filter(p -> p.getAccountId() != null && p.getAccountId().equals(entry.getAccountId()))
                .filter(p -> p.getApprovedAmount() != null && p.getApprovedAmount().compareTo(statementAmount) == 0)
                .findFirst()
                .ifPresent(p -> {
                    entry.setMatchedPaymentId(p.getPaymentId());
                    entry.setStatus(BankStatementEntry.ReconciliationStatus.SUGGESTED);
                });
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(value.trim().replace("\"", "").replace(",", ""));
    }
}
