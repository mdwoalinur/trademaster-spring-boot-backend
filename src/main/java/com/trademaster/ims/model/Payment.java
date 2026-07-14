
package com.trademaster.ims.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    public enum PaymentDirection { RECEIVE, PAY, REFUND, TRANSFER }
    public enum PaymentType { SALE, PURCHASE, EXPENSE, POS, CUSTOMER_ADVANCE, SUPPLIER_ADVANCE, REFUND, ADJUSTMENT, ACCOUNT_TRANSFER }
    public enum PaymentStatus { PAID, UNPAID, PARTIAL, REFUNDED }
    public enum PaymentMethod { CASH, BANK, MOBILE_BANKING, CHEQUE, BANK_TRANSFER, CARD }
    public enum PartyType { CUSTOMER, SUPPLIER, VENDOR, EMPLOYEE, INTERNAL, OTHER }
    public enum PaymentApprovalStatus { DRAFT, PENDING_APPROVAL, RETURNED_FOR_CORRECTION, APPROVED, REJECTED }
    public enum PaymentTransactionStatus { PENDING, POSTED, FAILED, CANCELLED, VOIDED, REVERSED }
    public enum ReconciliationStatus { UNRECONCILED, RECONCILED, MISMATCH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "voucher_no", unique = true, length = 100)
    private String voucherNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 30)
    private PaymentDirection direction = PaymentDirection.RECEIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", length = 30)
    private PartyType partyType;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "payment_date", nullable = false)
    private LocalDateTime paymentDate = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "requested_amount", precision = 19, scale = 4)
    private BigDecimal requestedAmount = BigDecimal.ZERO;

    @Column(name = "approved_amount", precision = 19, scale = 4)
    private BigDecimal approvedAmount;

    @Column(name = "currency_code", length = 10)
    private String currencyCode = "BDT";

    @Column(name = "exchange_rate", precision = 19, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "destination_account_id")
    private Long destinationAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(name = "transaction_reference", length = 150)
    private String transactionReference;

    @Column(name = "reference_no", length = 100)
    private String referenceNo;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 30)
    private PaymentApprovalStatus approvalStatus = PaymentApprovalStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 30)
    private PaymentTransactionStatus transactionStatus = PaymentTransactionStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNRECONCILED;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "received_by", nullable = false)
    private Long receivedBy;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by")
    private Long rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;
    @Column(name = "original_payment_id")
    private Long originalPaymentId;

    @Column(name = "reversal_payment_id")
    private Long reversalPaymentId;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @Column(name = "cash_drawer", length = 100)
    private String cashDrawer;

    @Column(name = "received_amount", precision = 19, scale = 4)
    private BigDecimal receivedAmount;

    @Column(name = "change_amount", precision = 19, scale = 4)
    private BigDecimal changeAmount;

    @Column(name = "transfer_date")
    private LocalDateTime transferDate;

    @Column(name = "sender_receiver_reference", length = 150)
    private String senderReceiverReference;

    @Column(name = "cheque_date")
    private LocalDateTime chequeDate;

    @Column(name = "expected_clearing_date")
    private LocalDateTime expectedClearingDate;

    @Column(name = "cheque_status", length = 30)
    private String chequeStatus;

    @Column(name = "approval_code", length = 100)
    private String approvalCode;

    @Column(name = "terminal_reference", length = 150)
    private String terminalReference;


    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "cheque_number", length = 100)
    private String chequeNumber;

    @Column(name = "mobile_provider", length = 100)
    private String mobileProvider;

    @Column(name = "mobile_transaction_id", length = 150)
    private String mobileTransactionId;

    @Column(name = "card_type", length = 50)
    private String cardType;

    @Column(name = "card_last_four", length = 4)
    private String cardLastFour;

    @Column(name = "gateway_reference", length = 150)
    private String gatewayReference;

    @Version
    @Column(name = "version")
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Transient
    private List<PaymentAllocation> allocations = new ArrayList<>();

    // ==================== Constructors ====================
    public Payment() {}

    public Payment(PaymentType paymentType, Long referenceId, BigDecimal amount, 
                   PaymentMethod paymentMethod, Long receivedBy) {
        this.paymentType = paymentType;
        this.referenceId = referenceId;
        this.amount = amount;
        this.requestedAmount = amount;
        this.paymentMethod = paymentMethod;
        this.receivedBy = receivedBy;
        this.createdBy = receivedBy;
        this.paymentDate = LocalDateTime.now();
        this.paymentStatus = PaymentStatus.UNPAID;
    }

    // ==================== Getters and Setters ====================
    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }
    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public PaymentDirection getDirection() { return direction; }
    public void setDirection(PaymentDirection direction) { this.direction = direction; }
    public PaymentType getPaymentType() { return paymentType; }
    public void setPaymentType(PaymentType paymentType) { this.paymentType = paymentType; }
    public PartyType getPartyType() { return partyType; }
    public void setPartyType(PartyType partyType) { this.partyType = partyType; }
    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public Long getReferenceId() { return referenceId; }
    public void setReferenceId(Long referenceId) { this.referenceId = referenceId; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public void setApprovedAmount(BigDecimal approvedAmount) { this.approvedAmount = approvedAmount; }
    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public BigDecimal getExchangeRate() { return exchangeRate; }
    public void setExchangeRate(BigDecimal exchangeRate) { this.exchangeRate = exchangeRate; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getDestinationAccountId() { return destinationAccountId; }
    public void setDestinationAccountId(Long destinationAccountId) { this.destinationAccountId = destinationAccountId; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }
    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public PaymentApprovalStatus getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(PaymentApprovalStatus approvalStatus) { this.approvalStatus = approvalStatus; }
    public PaymentTransactionStatus getTransactionStatus() { return transactionStatus; }
    public void setTransactionStatus(PaymentTransactionStatus transactionStatus) { this.transactionStatus = transactionStatus; }
    public ReconciliationStatus getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(ReconciliationStatus reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getWarehouseId() { return warehouseId; }
    public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
    public Long getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Long receivedBy) { this.receivedBy = receivedBy; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Long getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Long getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public Long getRejectedBy() { return rejectedBy; }
    public void setRejectedBy(Long rejectedBy) { this.rejectedBy = rejectedBy; }
    public LocalDateTime getRejectedAt() { return rejectedAt; }
    public void setRejectedAt(LocalDateTime rejectedAt) { this.rejectedAt = rejectedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public Long getPostedBy() { return postedBy; }
    public void setPostedBy(Long postedBy) { this.postedBy = postedBy; }
    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }
    public Long getVoidedBy() { return voidedBy; }
    public void setVoidedBy(Long voidedBy) { this.voidedBy = voidedBy; }
    public LocalDateTime getVoidedAt() { return voidedAt; }
    public void setVoidedAt(LocalDateTime voidedAt) { this.voidedAt = voidedAt; }
    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getChequeNumber() { return chequeNumber; }
    public void setChequeNumber(String chequeNumber) { this.chequeNumber = chequeNumber; }
    public String getMobileProvider() { return mobileProvider; }
    public void setMobileProvider(String mobileProvider) { this.mobileProvider = mobileProvider; }
    public String getMobileTransactionId() { return mobileTransactionId; }
    public void setMobileTransactionId(String mobileTransactionId) { this.mobileTransactionId = mobileTransactionId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getCardLastFour() { return cardLastFour; }
    public void setCardLastFour(String cardLastFour) { this.cardLastFour = cardLastFour; }
    public String getGatewayReference() { return gatewayReference; }
    public void setGatewayReference(String gatewayReference) { this.gatewayReference = gatewayReference; }
    public Long getOriginalPaymentId() { return originalPaymentId; }
    public void setOriginalPaymentId(Long originalPaymentId) { this.originalPaymentId = originalPaymentId; }
    public Long getReversalPaymentId() { return reversalPaymentId; }
    public void setReversalPaymentId(Long reversalPaymentId) { this.reversalPaymentId = reversalPaymentId; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public String getCashDrawer() { return cashDrawer; }
    public void setCashDrawer(String cashDrawer) { this.cashDrawer = cashDrawer; }
    public BigDecimal getReceivedAmount() { return receivedAmount; }
    public void setReceivedAmount(BigDecimal receivedAmount) { this.receivedAmount = receivedAmount; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public void setChangeAmount(BigDecimal changeAmount) { this.changeAmount = changeAmount; }
    public LocalDateTime getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDateTime transferDate) { this.transferDate = transferDate; }
    public String getSenderReceiverReference() { return senderReceiverReference; }
    public void setSenderReceiverReference(String senderReceiverReference) { this.senderReceiverReference = senderReceiverReference; }
    public LocalDateTime getChequeDate() { return chequeDate; }
    public void setChequeDate(LocalDateTime chequeDate) { this.chequeDate = chequeDate; }
    public LocalDateTime getExpectedClearingDate() { return expectedClearingDate; }
    public void setExpectedClearingDate(LocalDateTime expectedClearingDate) { this.expectedClearingDate = expectedClearingDate; }
    public String getChequeStatus() { return chequeStatus; }
    public void setChequeStatus(String chequeStatus) { this.chequeStatus = chequeStatus; }
    public String getApprovalCode() { return approvalCode; }
    public void setApprovalCode(String approvalCode) { this.approvalCode = approvalCode; }
    public String getTerminalReference() { return terminalReference; }
    public void setTerminalReference(String terminalReference) { this.terminalReference = terminalReference; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public List<PaymentAllocation> getAllocations() { return allocations; }
    public void setAllocations(List<PaymentAllocation> allocations) {
        this.allocations = allocations == null ? new ArrayList<>() : allocations;
    }
}

