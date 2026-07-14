package com.trademaster.ims.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_statement_entries",
        indexes = {
                @Index(name = "idx_bank_statement_account_date", columnList = "account_id,transaction_date"),
                @Index(name = "idx_bank_statement_reference", columnList = "account_id,statement_reference,transaction_date")
        })
public class BankStatementEntry {
    public enum ReconciliationStatus { UNMATCHED, SUGGESTED, MATCHED, RECONCILED, MISMATCH, IGNORED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "statement_entry_id")
    private Long statementEntryId;
    @Column(name = "account_id", nullable = false)
    private Long accountId;
    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;
    @Column(name = "value_date")
    private LocalDate valueDate;
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    @Column(name = "statement_reference", length = 150)
    private String statementReference;
    @Column(name = "debit_amount", precision = 19, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;
    @Column(name = "credit_amount", precision = 19, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;
    @Column(name = "external_balance", precision = 19, scale = 4)
    private BigDecimal externalBalance = BigDecimal.ZERO;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReconciliationStatus status = ReconciliationStatus.UNMATCHED;
    @Column(name = "matched_payment_id")
    private Long matchedPaymentId;
    @Column(name = "import_batch_id", length = 100)
    private String importBatchId;
    @Column(name = "imported_by")
    private Long importedBy;
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt = LocalDateTime.now();
    @Column(name = "reconciled_by")
    private Long reconciledBy;
    @Column(name = "reconciled_at")
    private LocalDateTime reconciledAt;
    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    public Long getStatementEntryId() { return statementEntryId; }
    public void setStatementEntryId(Long statementEntryId) { this.statementEntryId = statementEntryId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public LocalDate getValueDate() { return valueDate; }
    public void setValueDate(LocalDate valueDate) { this.valueDate = valueDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatementReference() { return statementReference; }
    public void setStatementReference(String statementReference) { this.statementReference = statementReference; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public BigDecimal getExternalBalance() { return externalBalance; }
    public void setExternalBalance(BigDecimal externalBalance) { this.externalBalance = externalBalance; }
    public ReconciliationStatus getStatus() { return status; }
    public void setStatus(ReconciliationStatus status) { this.status = status; }
    public Long getMatchedPaymentId() { return matchedPaymentId; }
    public void setMatchedPaymentId(Long matchedPaymentId) { this.matchedPaymentId = matchedPaymentId; }
    public String getImportBatchId() { return importBatchId; }
    public void setImportBatchId(String importBatchId) { this.importBatchId = importBatchId; }
    public Long getImportedBy() { return importedBy; }
    public void setImportedBy(Long importedBy) { this.importedBy = importedBy; }
    public LocalDateTime getImportedAt() { return importedAt; }
    public void setImportedAt(LocalDateTime importedAt) { this.importedAt = importedAt; }
    public Long getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(Long reconciledBy) { this.reconciledBy = reconciledBy; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}
