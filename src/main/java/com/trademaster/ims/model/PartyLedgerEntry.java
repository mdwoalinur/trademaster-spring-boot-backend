package com.trademaster.ims.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "party_ledger_entries")
public class PartyLedgerEntry {

    public enum PartyType { CUSTOMER, SUPPLIER, VENDOR }
    public enum EntryType { SALE_INVOICE, PURCHASE_BILL, EXPENSE_BILL, PAYMENT_RECEIVED, PAYMENT_MADE, REFUND, CREDIT_NOTE, DEBIT_NOTE, REVERSAL, ADJUSTMENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "party_ledger_entry_id")
    private Long partyLedgerEntryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 30)
    private PartyType partyType;

    @Column(name = "party_id", nullable = false)
    private Long partyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 40)
    private PaymentAllocation.ReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "document_amount", precision = 19, scale = 4)
    private BigDecimal documentAmount = BigDecimal.ZERO;

    @Column(name = "debit_amount", precision = 19, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", precision = 19, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 50)
    private EntryType entryType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt = LocalDateTime.now();

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "reversal_of_entry_id")
    private Long reversalOfEntryId;

    public Long getPartyLedgerEntryId() { return partyLedgerEntryId; }
    public void setPartyLedgerEntryId(Long partyLedgerEntryId) { this.partyLedgerEntryId = partyLedgerEntryId; }
    public PartyType getPartyType() { return partyType; }
    public void setPartyType(PartyType partyType) { this.partyType = partyType; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public PaymentAllocation.ReferenceType getReferenceType() { return referenceType; }
    public void setReferenceType(PaymentAllocation.ReferenceType referenceType) { this.referenceType = referenceType; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public BigDecimal getDocumentAmount() { return documentAmount; }
    public void setDocumentAmount(BigDecimal documentAmount) { this.documentAmount = documentAmount; }
    public BigDecimal getDebitAmount() { return debitAmount; }
    public void setDebitAmount(BigDecimal debitAmount) { this.debitAmount = debitAmount; }
    public BigDecimal getCreditAmount() { return creditAmount; }
    public void setCreditAmount(BigDecimal creditAmount) { this.creditAmount = creditAmount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public EntryType getEntryType() { return entryType; }
    public void setEntryType(EntryType entryType) { this.entryType = entryType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getReversalOfEntryId() { return reversalOfEntryId; }
    public void setReversalOfEntryId(Long reversalOfEntryId) { this.reversalOfEntryId = reversalOfEntryId; }
}
