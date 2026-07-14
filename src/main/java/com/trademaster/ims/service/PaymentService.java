package com.trademaster.ims.service;

import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.Expense;
import com.trademaster.ims.model.AccountLedgerEntry;
import com.trademaster.ims.model.Customer;
import com.trademaster.ims.model.FinancialAccount;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.model.PaymentApprovalHistory;
import com.trademaster.ims.model.PartyLedgerEntry;
import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.model.Role;
import com.trademaster.ims.model.Sale;
import com.trademaster.ims.model.Supplier;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.AccountLedgerEntryRepository;
import com.trademaster.ims.repository.CustomerRepository;
import com.trademaster.ims.repository.ExpenseRepository;
import com.trademaster.ims.repository.FinancialAccountRepository;
import com.trademaster.ims.repository.PartyLedgerEntryRepository;
import com.trademaster.ims.repository.PaymentAllocationRepository;
import com.trademaster.ims.repository.PaymentApprovalHistoryRepository;
import com.trademaster.ims.repository.PaymentRepository;
import com.trademaster.ims.repository.PurchaseRepository;
import com.trademaster.ims.repository.RoleRepository;
import com.trademaster.ims.repository.SaleRepository;
import com.trademaster.ims.repository.SupplierRepository;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentAllocationRepository allocationRepository;
    @Autowired private PaymentApprovalHistoryRepository historyRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired private AccountLedgerEntryRepository accountLedgerRepository;
    @Autowired private PartyLedgerEntryRepository partyLedgerRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private AuthContextService authContextService;

    public Page<Payment> getPayments(Pageable pageable, String type, String status, String search) {
        if (search != null && !search.isBlank()) {
            return paymentRepository.search(search.toLowerCase(Locale.ROOT), pageable);
        }
        if (type != null && !type.isBlank()) {
            try {
                return paymentRepository.findByPaymentType(Payment.PaymentType.valueOf(type), pageable);
            } catch (IllegalArgumentException ignored) {
                return paymentRepository.findAll(pageable);
            }
        }
        if (status != null && !status.isBlank()) {
            try {
                return paymentRepository.findByApprovalStatus(Payment.PaymentApprovalStatus.valueOf(status), pageable);
            } catch (IllegalArgumentException ignored) {
                try {
                    return paymentRepository.findByPaymentStatus(Payment.PaymentStatus.valueOf(status), pageable);
                } catch (IllegalArgumentException ignoredAgain) {
                    return paymentRepository.findAll(pageable);
                }
            }
        }
        return paymentRepository.findAll(pageable);
    }

    public Page<Payment> getPendingApprovals(Pageable pageable) {
        return paymentRepository.findByApprovalStatus(Payment.PaymentApprovalStatus.PENDING_APPROVAL, pageable);
    }

    public Payment getPaymentById(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
    }

    public List<PaymentApprovalHistory> getPaymentHistory(Long id) {
        return historyRepository.findByPaymentPaymentIdOrderByActedAtAsc(id);
    }

    public List<PaymentAllocation> getAllocations(Long id) {
        return allocationRepository.findByPaymentPaymentId(id);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Payment")
    public Payment createPayment(Payment payment) {
        Long userId = authContextService.getCurrentUserId();
        prepareNewPayment(payment, userId);
        validateDraftPayment(payment);

        Payment saved = paymentRepository.save(payment);
        assignFinalVoucherNo(saved);
        replaceAllocations(saved, payment.getAllocations(), normalizedRequestedAmount(saved));
        saved = paymentRepository.save(saved);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.CREATED, null,
                saved.getApprovalStatus().name(), "Payment request created", userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Payment")
    public Payment updatePayment(Long id, Payment paymentDetails) {
        Long userId = authContextService.getCurrentUserId();
        Payment existing = getPaymentById(id);
        if (existing.getApprovalStatus() != Payment.PaymentApprovalStatus.DRAFT) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only draft payments can be edited");
        }

        existing.setDirection(paymentDetails.getDirection());
        existing.setPaymentType(paymentDetails.getPaymentType());
        existing.setPartyType(paymentDetails.getPartyType());
        existing.setPartyId(paymentDetails.getPartyId());
        existing.setReferenceId(paymentDetails.getReferenceId());
        existing.setPaymentDate(paymentDetails.getPaymentDate());
        existing.setAmount(nonNull(paymentDetails.getAmount()));
        existing.setRequestedAmount(nonNull(paymentDetails.getRequestedAmount()).compareTo(BigDecimal.ZERO) > 0
                ? paymentDetails.getRequestedAmount()
                : existing.getAmount());
        existing.setCurrencyCode(defaultText(paymentDetails.getCurrencyCode(), "BDT"));
        existing.setExchangeRate(paymentDetails.getExchangeRate() == null ? BigDecimal.ONE : paymentDetails.getExchangeRate());
        existing.setAccountId(paymentDetails.getAccountId());
        existing.setDestinationAccountId(paymentDetails.getDestinationAccountId());
        existing.setPaymentMethod(paymentDetails.getPaymentMethod());
        existing.setTransactionReference(paymentDetails.getTransactionReference());
        existing.setCashDrawer(paymentDetails.getCashDrawer());
        existing.setReceivedAmount(paymentDetails.getReceivedAmount());
        existing.setChangeAmount(paymentDetails.getChangeAmount());
        existing.setTransferDate(paymentDetails.getTransferDate());
        existing.setSenderReceiverReference(paymentDetails.getSenderReceiverReference());
        existing.setChequeDate(paymentDetails.getChequeDate());
        existing.setExpectedClearingDate(paymentDetails.getExpectedClearingDate());
        existing.setChequeStatus(paymentDetails.getChequeStatus());
        existing.setBankName(paymentDetails.getBankName());
        existing.setChequeNumber(paymentDetails.getChequeNumber());
        existing.setMobileProvider(paymentDetails.getMobileProvider());
        existing.setMobileTransactionId(paymentDetails.getMobileTransactionId());
        existing.setCardType(paymentDetails.getCardType());
        existing.setCardLastFour(paymentDetails.getCardLastFour());
        existing.setGatewayReference(paymentDetails.getGatewayReference());
        existing.setApprovalCode(paymentDetails.getApprovalCode());
        existing.setTerminalReference(paymentDetails.getTerminalReference());
        existing.setReferenceNo(paymentDetails.getReferenceNo());
        existing.setNotes(paymentDetails.getNotes());
        existing.setWarehouseId(paymentDetails.getWarehouseId());
        existing.setUpdatedAt(LocalDateTime.now());
        existing.setReceivedBy(userId);

        validateDraftPayment(existing);
        Payment saved = paymentRepository.save(existing);
        replaceAllocations(saved, paymentDetails.getAllocations(), normalizedRequestedAmount(saved));
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.UPDATED, null,
                saved.getApprovalStatus().name(), "Payment draft updated", userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "SUBMIT", entityType = "Payment")
    public Payment submitPayment(Long id) {
        Long userId = authContextService.getCurrentUserId();
        Payment payment = getPaymentById(id);
        if (payment.getApprovalStatus() != Payment.PaymentApprovalStatus.DRAFT) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only draft payments can be submitted");
        }
        validateDraftPayment(payment);

        String old = payment.getApprovalStatus().name();
        payment.setApprovalStatus(Payment.PaymentApprovalStatus.PENDING_APPROVAL);
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.PENDING);
        payment.setPaymentStatus(Payment.PaymentStatus.UNPAID);
        payment.setSubmittedBy(userId);
        payment.setSubmittedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.SUBMITTED, old,
                saved.getApprovalStatus().name(), "Submitted for admin approval", userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "APPROVE_POST", entityType = "Payment")
    public Payment approveAndPost(Long id, BigDecimal approvedAmount, String comments) {
        return approveAndPost(id, approvedAmount, null, null, comments);
    }

    @Transactional
    @Auditable(action = "APPROVE_POST", entityType = "Payment")
    public Payment approveAndPost(Long id, BigDecimal approvedAmount, Long accountId, Long destinationAccountId, String comments) {
        Long userId = authContextService.getCurrentUserId();
        requirePaymentAdmin(userId);

        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found with id: " + id));

        if (payment.getApprovalStatus() != Payment.PaymentApprovalStatus.PENDING_APPROVAL) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only pending payment requests can be approved");
        }
        if (accountId != null) {
            payment.setAccountId(accountId);
        }
        if (destinationAccountId != null) {
            payment.setDestinationAccountId(destinationAccountId);
        }

        BigDecimal requested = normalizedRequestedAmount(payment);
        BigDecimal approved = approvedAmount == null || approvedAmount.compareTo(BigDecimal.ZERO) <= 0 ? requested : approvedAmount;
        if (approved.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Approved amount must be greater than zero");
        }
        if (approved.compareTo(requested) > 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Approved amount cannot exceed requested amount");
        }

        List<PaymentAllocation> allocations = validatedStoredAllocations(payment, approved);
        postAccountLedger(payment, approved, userId);

        String old = payment.getApprovalStatus().name();
        LocalDateTime now = LocalDateTime.now();
        payment.setApprovedAmount(approved);
        payment.setApprovalStatus(Payment.PaymentApprovalStatus.APPROVED);
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.POSTED);
        payment.setPaymentStatus(Payment.PaymentStatus.PAID);
        payment.setApprovedBy(userId);
        payment.setApprovedAt(now);
        payment.setPostedBy(userId);
        payment.setPostedAt(now);
        Payment saved = paymentRepository.save(payment);

        recalculateReferenceSettlement(allocations);
        postPartyLedger(saved, allocations, userId);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.APPROVED, old,
                saved.getApprovalStatus().name(), safeComments(comments, "Payment approved"), userId);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.POSTED, Payment.PaymentTransactionStatus.PENDING.name(),
                saved.getTransactionStatus().name(), "Payment posted after approval", userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "REJECT", entityType = "Payment")
    public Payment rejectPayment(Long id, String reason) {
        Long userId = authContextService.getCurrentUserId();
        requirePaymentAdmin(userId);
        if (reason == null || reason.isBlank()) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
        if (payment.getApprovalStatus() != Payment.PaymentApprovalStatus.PENDING_APPROVAL) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only pending payment requests can be rejected");
        }
        String old = payment.getApprovalStatus().name();
        payment.setApprovalStatus(Payment.PaymentApprovalStatus.REJECTED);
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.CANCELLED);
        payment.setPaymentStatus(Payment.PaymentStatus.UNPAID);
        payment.setRejectedBy(userId);
        payment.setRejectedAt(LocalDateTime.now());
        payment.setRejectionReason(reason);
        Payment saved = paymentRepository.save(payment);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.REJECTED, old,
                saved.getApprovalStatus().name(), reason, userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Payment")
    public void deletePayment(Long id) {
        Long userId = authContextService.getCurrentUserId();
        Payment payment = getPaymentById(id);
        if (payment.getApprovalStatus() != Payment.PaymentApprovalStatus.DRAFT
                || payment.getTransactionStatus() != Payment.PaymentTransactionStatus.PENDING) {
            throw new ApiResponseException(HttpStatus.CONFLICT,
                    "Financial payment records cannot be hard deleted after workflow starts. Cancel, reject, or reverse instead.");
        }
        String old = payment.getTransactionStatus().name();
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.CANCELLED);
        payment.setPaymentStatus(Payment.PaymentStatus.UNPAID);
        payment.setVoidReason("Draft payment cancelled instead of deleted to preserve financial audit history");
        payment.setVoidedBy(userId);
        payment.setVoidedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.VOIDED, old,
                saved.getTransactionStatus().name(), "Draft payment cancelled; no financial posting was made", userId);
    }

    @Transactional
    public List<Payment> createBulkPayments(List<Payment> payments) {
        List<Payment> createdPayments = new ArrayList<>();
        for (Payment payment : payments) {
            createdPayments.add(createPayment(payment));
        }
        return createdPayments;
    }

    @Transactional
    public Payment createSubmittedPaymentRequest(Payment payment) {
        Payment saved = createPayment(payment);
        return submitPayment(saved.getPaymentId());
    }

    @Transactional
    public Payment createPosPaymentRequest(Sale sale, BigDecimal requestedAmount, String paymentMethod, String notes) {
        if (sale == null || sale.getSaleId() == null || requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        Payment payment = new Payment();
        payment.setDirection(Payment.PaymentDirection.RECEIVE);
        payment.setPaymentType(Payment.PaymentType.POS);
        payment.setPartyType(Payment.PartyType.CUSTOMER);
        payment.setPartyId(sale.getCustomerId());
        payment.setReferenceId(sale.getSaleId());
        payment.setReferenceNo(sale.getInvoiceNo());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setAmount(requestedAmount);
        payment.setRequestedAmount(requestedAmount);
        payment.setPaymentMethod(parsePaymentMethod(paymentMethod));
        payment.setNotes(defaultText(notes, "POS payment request awaiting admin approval"));
        payment.setWarehouseId(sale.getWarehouseId());
        return createSubmittedPaymentRequest(payment);
    }

    public Map<String, Object> getReferencePaymentSummary(PaymentAllocation.ReferenceType referenceType, Long referenceId, BigDecimal totalAmount) {
        BigDecimal posted = allocationRepository.getPostedAmountForReference(referenceType, referenceId);
        BigDecimal pending = allocationRepository.getPendingAmountForReference(referenceType, referenceId);
        BigDecimal total = nonNull(totalAmount);
        BigDecimal due = total.subtract(posted);
        if (due.compareTo(BigDecimal.ZERO) < 0) {
            due = BigDecimal.ZERO;
        }
        return Map.of(
                "referenceType", referenceType.name(),
                "referenceId", referenceId,
                "totalAmount", total,
                "postedPaidAmount", posted,
                "pendingApprovalAmount", pending,
                "dueAmount", due,
                "settlementStatus", settlementStatus(posted, total)
        );
    }

    public Page<Map<String, Object>> searchParties(String paymentTypeValue, String search, Pageable pageable) {
        Payment.PaymentType paymentType = parsePaymentType(paymentTypeValue);
        String keyword = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();
        if (partyKindFor(paymentType) == Payment.PartyType.CUSTOMER) {
            rows = customerRepository.findAll().stream()
                    .filter(c -> keyword.isBlank()
                            || contains(c.getCustomerName(), keyword)
                            || contains(c.getCustomerCode(), keyword)
                            || contains(c.getEmail(), keyword)
                            || contains(c.getPhone(), keyword)
                            || contains(c.getMobile(), keyword))
                    .map(c -> partyRow("CUSTOMER", c.getCustomerId(), c.getCustomerName(), c.getCustomerCode(), c.getEmail(), c.getPhone(), c.getCurrentBalance()))
                    .collect(Collectors.toList());
        } else if (partyKindFor(paymentType) == Payment.PartyType.SUPPLIER || partyKindFor(paymentType) == Payment.PartyType.VENDOR) {
            Payment.PartyType partyType = partyKindFor(paymentType);
            rows = supplierRepository.findAll().stream()
                    .filter(s -> keyword.isBlank()
                            || contains(s.getSupplierName(), keyword)
                            || contains(s.getSupplierCode(), keyword)
                            || contains(s.getEmail(), keyword)
                            || contains(s.getPhone(), keyword))
                    .map(s -> partyRow(partyType.name(), s.getSupplierId(), s.getSupplierName(), s.getSupplierCode(), s.getEmail(), s.getPhone(), s.getCurrentBalance()))
                    .collect(Collectors.toList());
        }
        return pageRows(rows, pageable);
    }

    public Page<Map<String, Object>> getOutstandingReferences(String paymentTypeValue, Long partyId, String search, Pageable pageable) {
        Payment.PaymentType paymentType = parsePaymentType(paymentTypeValue);
        PaymentAllocation.ReferenceType referenceType = referenceTypeFor(paymentType);
        String keyword = search == null ? "" : search.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> rows = new ArrayList<>();

        if (referenceType == PaymentAllocation.ReferenceType.SALE) {
            rows = saleRepository.findAll().stream()
                    .filter(s -> partyId == null || partyId.equals(s.getCustomerId()))
                    .filter(s -> s.getStatus() != Sale.SaleStatus.CANCELLED)
                    .filter(s -> keyword.isBlank() || contains(s.getInvoiceNo(), keyword))
                    .map(s -> referenceRow(referenceType, s.getSaleId(), s.getInvoiceNo(), s.getSaleDate(), s.getCustomerId(), customerName(s.getCustomerId()), nonNull(s.getTotalAmount()), null))
                    .filter(this::hasDue)
                    .collect(Collectors.toList());
        } else if (referenceType == PaymentAllocation.ReferenceType.PURCHASE) {
            rows = purchaseRepository.findAll().stream()
                    .filter(p -> partyId == null || partyId.equals(p.getSupplierId()))
                    .filter(p -> p.getStatus() == Purchase.PurchaseStatus.RECEIVED)
                    .filter(p -> keyword.isBlank() || contains(p.getPurchaseOrderNo(), keyword))
                    .map(p -> referenceRow(referenceType, p.getPurchaseId(), p.getPurchaseOrderNo(), p.getPurchaseDate(), p.getSupplierId(), supplierName(p.getSupplierId()), nonNull(p.getTotalAmount()), null))
                    .filter(this::hasDue)
                    .collect(Collectors.toList());
        } else if (referenceType == PaymentAllocation.ReferenceType.EXPENSE) {
            rows = expenseRepository.findAll().stream()
                    .filter(e -> partyId == null || partyId.equals(e.getVendorId()))
                    .filter(e -> e.getStatus() == Expense.ExpenseStatus.APPROVED)
                    .filter(e -> keyword.isBlank() || contains(e.getExpenseNo(), keyword))
                    .map(e -> referenceRow(referenceType, e.getExpenseId(), e.getExpenseNo(), e.getExpenseDate(), e.getVendorId(), supplierName(e.getVendorId()), nonNull(e.getGrandTotal()), null))
                    .filter(this::hasDue)
                    .collect(Collectors.toList());
        }
        return pageRows(rows, pageable);
    }

    private void prepareNewPayment(Payment payment, Long userId) {
        payment.setPaymentId(null);
        payment.setVoucherNo(defaultText(payment.getVoucherNo(), generateTemporaryVoucherNo()));
        payment.setDirection(payment.getDirection() == null ? defaultDirection(payment.getPaymentType()) : payment.getDirection());
        payment.setPaymentDate(payment.getPaymentDate() == null ? LocalDateTime.now() : payment.getPaymentDate());
        payment.setAmount(nonNull(payment.getAmount()));
        payment.setRequestedAmount(nonNull(payment.getRequestedAmount()).compareTo(BigDecimal.ZERO) > 0
                ? payment.getRequestedAmount()
                : payment.getAmount());
        payment.setApprovedAmount(null);
        payment.setCurrencyCode(defaultText(payment.getCurrencyCode(), "BDT"));
        payment.setExchangeRate(payment.getExchangeRate() == null ? BigDecimal.ONE : payment.getExchangeRate());
        payment.setPaymentMethod(payment.getPaymentMethod() == null ? Payment.PaymentMethod.CASH : payment.getPaymentMethod());
        payment.setApprovalStatus(payment.getApprovalStatus() == Payment.PaymentApprovalStatus.PENDING_APPROVAL
                ? Payment.PaymentApprovalStatus.PENDING_APPROVAL
                : Payment.PaymentApprovalStatus.DRAFT);
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.PENDING);
        payment.setPaymentStatus(Payment.PaymentStatus.UNPAID);
        payment.setReconciliationStatus(Payment.ReconciliationStatus.UNRECONCILED);
        payment.setCompanyId(authContextService.getCurrentCompanyId());
        payment.setReceivedBy(userId);
        payment.setCreatedBy(userId);
        payment.setSubmittedBy(payment.getApprovalStatus() == Payment.PaymentApprovalStatus.PENDING_APPROVAL ? userId : null);
        payment.setSubmittedAt(payment.getApprovalStatus() == Payment.PaymentApprovalStatus.PENDING_APPROVAL ? LocalDateTime.now() : null);
    }

    private void validateDraftPayment(Payment payment) {
        if (payment.getPaymentType() == null) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Payment type is required");
        }
        if (normalizedRequestedAmount(payment).compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Payment amount must be greater than zero");
        }
        if (payment.getPaymentMethod() == null) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Payment method is required");
        }
        if (payment.getPaymentMethod() == Payment.PaymentMethod.BANK_TRANSFER
                && isBlank(payment.getTransactionReference())) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Bank transfer transaction reference is required");
        }
        if (payment.getPaymentMethod() == Payment.PaymentMethod.CHEQUE
                && isBlank(payment.getChequeNumber())) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Cheque number is required");
        }
        if (payment.getPaymentMethod() == Payment.PaymentMethod.MOBILE_BANKING
                && isBlank(payment.getMobileTransactionId())) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Mobile banking transaction ID is required");
        }
        if (payment.getPaymentType() != Payment.PaymentType.ACCOUNT_TRANSFER
                && payment.getPaymentType() != Payment.PaymentType.CUSTOMER_ADVANCE
                && payment.getPaymentType() != Payment.PaymentType.SUPPLIER_ADVANCE
                && payment.getPaymentType() != Payment.PaymentType.REFUND) {
            validateIncomingAllocations(payment, normalizedRequestedAmount(payment));
        }
    }

    private void validateReferenceExists(Payment payment) {
        switch (referenceTypeFor(payment.getPaymentType())) {
            case EXPENSE -> expenseRepository.findById(payment.getReferenceId())
                    .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Expense reference not found"));
            case SALE -> saleRepository.findById(payment.getReferenceId())
                    .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Sale reference not found"));
            case PURCHASE -> purchaseRepository.findById(payment.getReferenceId())
                    .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Purchase reference not found"));
            default -> {
                // Advance/refund-only flows are validated by their own workflow when enabled.
            }
        }
    }

    private void validateReferenceDue(Payment payment, BigDecimal approvedAmount) {
        PaymentAllocation.ReferenceType referenceType = referenceTypeFor(payment.getPaymentType());
        if (referenceType == PaymentAllocation.ReferenceType.EXPENSE) {
            Expense expense = expenseRepository.findById(payment.getReferenceId())
                    .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Expense reference not found"));
            if (expense.getStatus() != Expense.ExpenseStatus.APPROVED) {
                throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only approved expenses can be paid");
            }
            validateAmountWithinDue(referenceType, expense.getExpenseId(), nonNull(expense.getGrandTotal()), approvedAmount);
        } else if (referenceType == PaymentAllocation.ReferenceType.SALE) {
            Sale sale = saleRepository.findById(payment.getReferenceId())
                    .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Sale reference not found"));
            if (sale.getStatus() == Sale.SaleStatus.CANCELLED) {
                throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Cancelled sales cannot receive payment");
            }
            validateAmountWithinDue(referenceType, sale.getSaleId(), nonNull(sale.getTotalAmount()), approvedAmount);
        } else if (referenceType == PaymentAllocation.ReferenceType.PURCHASE) {
            Purchase purchase = purchaseRepository.findById(payment.getReferenceId())
                    .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Purchase reference not found"));
            if (purchase.getStatus() != Purchase.PurchaseStatus.RECEIVED) {
                throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only received purchases can be paid");
            }
            validateAmountWithinDue(referenceType, purchase.getPurchaseId(), nonNull(purchase.getTotalAmount()), approvedAmount);
        }
    }

    private void postAccountLedger(Payment payment, BigDecimal amount, Long userId) {
        if (payment.getAccountId() == null) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Financial account is required before posting");
        }
        FinancialAccount account = accountRepository.findByIdForUpdate(payment.getAccountId())
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Financial account not found"));
        if (account.getStatus() != FinancialAccount.AccountStatus.ACTIVE) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only active accounts can be used for posting");
        }

        if (payment.getDirection() == Payment.PaymentDirection.TRANSFER) {
            postTransferLedger(payment, account, amount, userId);
            return;
        }

        BigDecimal before = nonNull(account.getCurrentBalance());
        boolean moneyIn = payment.getDirection() == Payment.PaymentDirection.RECEIVE;
        BigDecimal after = moneyIn ? before.add(amount) : before.subtract(amount);
        if (!Boolean.TRUE.equals(account.getAllowOverdraft()) && after.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient account balance");
        }

        account.setCurrentBalance(after);
        accountRepository.save(account);

        AccountLedgerEntry entry = buildAccountLedger(payment, account.getAccountId(), amount, before, after, userId,
                moneyIn ? AccountLedgerEntry.EntryType.PAYMENT_RECEIPT : AccountLedgerEntry.EntryType.PAYMENT_DISBURSEMENT,
                moneyIn);
        accountLedgerRepository.save(entry);
    }

    private void postTransferLedger(Payment payment, FinancialAccount source, BigDecimal amount, Long userId) {
        if (payment.getDestinationAccountId() == null) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Destination account is required for account transfer");
        }
        if (payment.getDestinationAccountId().equals(source.getAccountId())) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Source and destination account cannot be the same");
        }
        FinancialAccount destination = accountRepository.findByIdForUpdate(payment.getDestinationAccountId())
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Destination account not found"));

        BigDecimal sourceBefore = nonNull(source.getCurrentBalance());
        BigDecimal sourceAfter = sourceBefore.subtract(amount);
        if (!Boolean.TRUE.equals(source.getAllowOverdraft()) && sourceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient source account balance");
        }
        BigDecimal destinationBefore = nonNull(destination.getCurrentBalance());
        BigDecimal destinationAfter = destinationBefore.add(amount);

        source.setCurrentBalance(sourceAfter);
        destination.setCurrentBalance(destinationAfter);
        accountRepository.save(source);
        accountRepository.save(destination);

        accountLedgerRepository.save(buildAccountLedger(payment, source.getAccountId(), amount, sourceBefore, sourceAfter,
                userId, AccountLedgerEntry.EntryType.TRANSFER_OUT, false));
        accountLedgerRepository.save(buildAccountLedger(payment, destination.getAccountId(), amount, destinationBefore, destinationAfter,
                userId, AccountLedgerEntry.EntryType.TRANSFER_IN, true));
    }

    private AccountLedgerEntry buildAccountLedger(Payment payment, Long accountId, BigDecimal amount,
                                                 BigDecimal before, BigDecimal after, Long userId,
                                                 AccountLedgerEntry.EntryType entryType, boolean debit) {
        AccountLedgerEntry entry = new AccountLedgerEntry();
        entry.setAccountId(accountId);
        entry.setPaymentId(payment.getPaymentId());
        entry.setEntryType(entryType);
        entry.setDirection(payment.getDirection());
        entry.setDebitAmount(debit ? amount : BigDecimal.ZERO);
        entry.setCreditAmount(debit ? BigDecimal.ZERO : amount);
        entry.setBalanceBefore(before);
        entry.setBalanceAfter(after);
        entry.setReferenceType(referenceTypeFor(payment.getPaymentType()));
        entry.setReferenceId(payment.getReferenceId());
        entry.setVoucherNo(payment.getVoucherNo());
        entry.setDescription(defaultText(payment.getNotes(), "Payment posted"));
        entry.setPostedBy(userId);
        entry.setPostedAt(LocalDateTime.now());
        entry.setCompanyId(payment.getCompanyId());
        return entry;
    }

    private void validateAmountWithinDue(PaymentAllocation.ReferenceType referenceType, Long referenceId,
                                         BigDecimal total, BigDecimal approvedAmount) {
        BigDecimal alreadyPosted = allocationRepository.getPostedAmountForReference(referenceType, referenceId);
        BigDecimal due = total.subtract(alreadyPosted);
        if (approvedAmount.compareTo(due) > 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Approved amount exceeds current due amount");
        }
    }

    private void recalculateReferenceSettlement(List<PaymentAllocation> allocations) {
        for (PaymentAllocation allocation : allocations) {
            recalculateReferenceSettlement(allocation.getReferenceType(), allocation.getReferenceId());
        }
    }

    private void recalculateReferenceSettlement(PaymentAllocation.ReferenceType referenceType, Long referenceId) {
        if (referenceType == PaymentAllocation.ReferenceType.EXPENSE) {
            Expense expense = expenseRepository.findById(referenceId).orElse(null);
            if (expense == null) return;
            BigDecimal paid = allocationRepository.getPostedAmountForReference(referenceType, expense.getExpenseId());
            BigDecimal total = nonNull(expense.getGrandTotal());
            expense.setPaymentStatus(toExpenseStatus(paid, total));
            expenseRepository.save(expense);
        } else if (referenceType == PaymentAllocation.ReferenceType.SALE) {
            Sale sale = saleRepository.findById(referenceId).orElse(null);
            if (sale == null) return;
            BigDecimal paid = allocationRepository.getPostedAmountForReference(referenceType, sale.getSaleId());
            BigDecimal total = nonNull(sale.getTotalAmount());
            sale.setPaidAmount(paid);
            sale.setDueAmount(total.subtract(paid).max(BigDecimal.ZERO));
            sale.setPaymentStatus(toSaleStatus(paid, total));
            saleRepository.save(sale);
        } else if (referenceType == PaymentAllocation.ReferenceType.PURCHASE) {
            Purchase purchase = purchaseRepository.findById(referenceId).orElse(null);
            if (purchase == null) return;
            BigDecimal paid = allocationRepository.getPostedAmountForReference(referenceType, purchase.getPurchaseId());
            BigDecimal total = nonNull(purchase.getTotalAmount());
            purchase.setPaidAmount(paid);
            purchase.setDueAmount(total.subtract(paid).max(BigDecimal.ZERO));
            purchase.setPaymentStatus(toPurchaseStatus(paid, total));
            purchaseRepository.save(purchase);
        }
    }

    private void postPartyLedger(Payment payment, List<PaymentAllocation> allocations, Long userId) {
        for (PaymentAllocation allocation : allocations) {
            postPartyLedger(payment, allocation, userId);
        }
    }

    private void postPartyLedger(Payment payment, PaymentAllocation allocation, Long userId) {
        PaymentAllocation.ReferenceType referenceType = allocation.getReferenceType();
        BigDecimal amount = nonNull(allocation.getAllocatedAmount());
        if (referenceType == PaymentAllocation.ReferenceType.SALE) {
            Sale sale = saleRepository.findById(allocation.getReferenceId()).orElse(null);
            if (sale == null || sale.getCustomerId() == null) return;
            Customer customer = customerRepository.findById(sale.getCustomerId()).orElse(null);
            BigDecimal balanceAfter = BigDecimal.ZERO;
            if (customer != null) {
                balanceAfter = nonNull(customer.getCurrentBalance()).subtract(amount);
                customer.setCurrentBalance(balanceAfter);
                customerRepository.save(customer);
            }
            savePartyLedger(PartyLedgerEntry.PartyType.CUSTOMER, sale.getCustomerId(), referenceType, sale.getSaleId(),
                    payment, nonNull(sale.getTotalAmount()), BigDecimal.ZERO, amount, balanceAfter,
                    PartyLedgerEntry.EntryType.PAYMENT_RECEIVED);
        } else if (referenceType == PaymentAllocation.ReferenceType.PURCHASE) {
            Purchase purchase = purchaseRepository.findById(allocation.getReferenceId()).orElse(null);
            if (purchase == null || purchase.getSupplierId() == null) return;
            Supplier supplier = supplierRepository.findById(purchase.getSupplierId()).orElse(null);
            BigDecimal balanceAfter = BigDecimal.ZERO;
            if (supplier != null) {
                balanceAfter = nonNull(supplier.getCurrentBalance()).subtract(amount);
                supplier.setCurrentBalance(balanceAfter);
                supplierRepository.save(supplier);
            }
            savePartyLedger(PartyLedgerEntry.PartyType.SUPPLIER, purchase.getSupplierId(), referenceType, purchase.getPurchaseId(),
                    payment, nonNull(purchase.getTotalAmount()), BigDecimal.ZERO, amount, balanceAfter,
                    PartyLedgerEntry.EntryType.PAYMENT_MADE);
        } else if (referenceType == PaymentAllocation.ReferenceType.EXPENSE) {
            Expense expense = expenseRepository.findById(allocation.getReferenceId()).orElse(null);
            if (expense == null || expense.getVendorId() == null) return;
            Supplier supplier = supplierRepository.findById(expense.getVendorId()).orElse(null);
            BigDecimal balanceAfter = BigDecimal.ZERO;
            if (supplier != null) {
                balanceAfter = nonNull(supplier.getCurrentBalance()).subtract(amount);
                supplier.setCurrentBalance(balanceAfter);
                supplierRepository.save(supplier);
            }
            savePartyLedger(PartyLedgerEntry.PartyType.VENDOR, expense.getVendorId(), referenceType, expense.getExpenseId(),
                    payment, nonNull(expense.getGrandTotal()), BigDecimal.ZERO, amount, balanceAfter,
                    PartyLedgerEntry.EntryType.PAYMENT_MADE);
        }
    }

    private void savePartyLedger(PartyLedgerEntry.PartyType partyType, Long partyId,
                                 PaymentAllocation.ReferenceType referenceType, Long referenceId,
                                 Payment payment, BigDecimal documentAmount, BigDecimal debit,
                                 BigDecimal credit, BigDecimal balanceAfter,
                                 PartyLedgerEntry.EntryType entryType) {
        PartyLedgerEntry entry = new PartyLedgerEntry();
        entry.setPartyType(partyType);
        entry.setPartyId(partyId);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setPaymentId(payment.getPaymentId());
        entry.setDocumentAmount(documentAmount);
        entry.setDebitAmount(debit);
        entry.setCreditAmount(credit);
        entry.setBalanceAfter(balanceAfter);
        entry.setEntryType(entryType);
        entry.setDescription(defaultText(payment.getNotes(), "Payment posted"));
        entry.setPostedAt(LocalDateTime.now());
        entry.setCompanyId(payment.getCompanyId());
        partyLedgerRepository.save(entry);
    }

    private void createDefaultAllocation(Payment payment, BigDecimal amount) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPayment(payment);
        allocation.setReferenceType(referenceTypeFor(payment.getPaymentType()));
        allocation.setReferenceId(payment.getReferenceId());
        allocation.setAllocatedAmount(amount);
        allocationRepository.save(allocation);
    }

    private void replaceDefaultAllocation(Payment payment, BigDecimal amount) {
        allocationRepository.deleteAll(allocationRepository.findByPaymentPaymentId(payment.getPaymentId()));
        createDefaultAllocation(payment, amount);
    }

    private void replaceAllocations(Payment payment, List<PaymentAllocation> incoming, BigDecimal expectedAmount) {
        List<PaymentAllocation> allocations = normalizeAllocations(payment, incoming, expectedAmount);
        allocationRepository.deleteAll(allocationRepository.findByPaymentPaymentId(payment.getPaymentId()));
        for (PaymentAllocation allocation : allocations) {
            allocation.setAllocationId(null);
            allocation.setPayment(payment);
            allocationRepository.save(allocation);
        }
        if (!allocations.isEmpty()) {
            PaymentAllocation first = allocations.get(0);
            payment.setReferenceId(first.getReferenceId());
            payment.setReferenceNo(defaultText(payment.getReferenceNo(), referenceNo(first.getReferenceType(), first.getReferenceId())));
        }
    }

    private List<PaymentAllocation> normalizeAllocations(Payment payment, List<PaymentAllocation> incoming, BigDecimal expectedAmount) {
        List<PaymentAllocation> source = incoming == null ? List.of() : incoming;
        if (source.isEmpty() && payment.getReferenceId() != null && payment.getReferenceId() > 0) {
            PaymentAllocation fallback = new PaymentAllocation();
            fallback.setReferenceType(referenceTypeFor(payment.getPaymentType()));
            fallback.setReferenceId(payment.getReferenceId());
            fallback.setAllocatedAmount(expectedAmount);
            source = List.of(fallback);
        }
        validateAllocationSet(payment, source, expectedAmount, payment.getPaymentId());
        List<PaymentAllocation> normalized = new ArrayList<>();
        for (PaymentAllocation item : source) {
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setReferenceType(item.getReferenceType() == null ? referenceTypeFor(payment.getPaymentType()) : item.getReferenceType());
            allocation.setReferenceId(item.getReferenceId());
            allocation.setAllocatedAmount(nonNull(item.getAllocatedAmount()));
            allocation.setDiscountAmount(nonNull(item.getDiscountAmount()));
            allocation.setWriteOffAmount(nonNull(item.getWriteOffAmount()));
            normalized.add(allocation);
        }
        return normalized;
    }

    private void validateIncomingAllocations(Payment payment, BigDecimal expectedAmount) {
        normalizeAllocations(payment, payment.getAllocations(), expectedAmount);
    }

    private List<PaymentAllocation> validatedStoredAllocations(Payment payment, BigDecimal expectedAmount) {
        List<PaymentAllocation> allocations = allocationRepository.findByPaymentPaymentId(payment.getPaymentId());
        validateAllocationSet(payment, allocations, expectedAmount, payment.getPaymentId());
        return allocations;
    }

    private void validateAllocationSet(Payment payment, List<PaymentAllocation> allocations, BigDecimal expectedAmount, Long currentPaymentId) {
        if (payment.getPaymentType() == Payment.PaymentType.ACCOUNT_TRANSFER
                || payment.getPaymentType() == Payment.PaymentType.CUSTOMER_ADVANCE
                || payment.getPaymentType() == Payment.PaymentType.SUPPLIER_ADVANCE
                || payment.getPaymentType() == Payment.PaymentType.REFUND) {
            return;
        }
        if (allocations == null || allocations.isEmpty()) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Select at least one outstanding reference");
        }
        BigDecimal total = BigDecimal.ZERO;
        Set<String> seen = new HashSet<>();
        PaymentAllocation.ReferenceType expectedType = referenceTypeFor(payment.getPaymentType());
        for (PaymentAllocation allocation : allocations) {
            PaymentAllocation.ReferenceType type = allocation.getReferenceType() == null ? expectedType : allocation.getReferenceType();
            if (type != expectedType) {
                throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Allocation reference type does not match payment type");
            }
            if (allocation.getReferenceId() == null || allocation.getReferenceId() <= 0) {
                throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Allocation reference is required");
            }
            if (!seen.add(type.name() + ":" + allocation.getReferenceId())) {
                throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Duplicate allocation reference selected");
            }
            BigDecimal amount = nonNull(allocation.getAllocatedAmount());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Allocation amount must be greater than zero");
            }
            BigDecimal due = currentDueFor(type, allocation.getReferenceId(), currentPaymentId);
            if (amount.compareTo(due) > 0) {
                throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Allocation exceeds current due for " + referenceNo(type, allocation.getReferenceId()));
            }
            total = total.add(amount);
        }
        if (total.compareTo(nonNull(expectedAmount)) != 0) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Allocation total must equal payment amount");
        }
    }

    private PaymentAllocation.ReferenceType referenceTypeFor(Payment.PaymentType paymentType) {
        if (paymentType == Payment.PaymentType.EXPENSE) {
            return PaymentAllocation.ReferenceType.EXPENSE;
        }
        if (paymentType == Payment.PaymentType.PURCHASE || paymentType == Payment.PaymentType.SUPPLIER_ADVANCE) {
            return PaymentAllocation.ReferenceType.PURCHASE;
        }
        if (paymentType == Payment.PaymentType.REFUND) {
            return PaymentAllocation.ReferenceType.REFUND;
        }
        return PaymentAllocation.ReferenceType.SALE;
    }

    private Payment.PaymentDirection defaultDirection(Payment.PaymentType paymentType) {
        if (paymentType == Payment.PaymentType.EXPENSE
                || paymentType == Payment.PaymentType.PURCHASE
                || paymentType == Payment.PaymentType.SUPPLIER_ADVANCE) {
            return Payment.PaymentDirection.PAY;
        }
        if (paymentType == Payment.PaymentType.REFUND) {
            return Payment.PaymentDirection.REFUND;
        }
        if (paymentType == Payment.PaymentType.ACCOUNT_TRANSFER) {
            return Payment.PaymentDirection.TRANSFER;
        }
        return Payment.PaymentDirection.RECEIVE;
    }

    private Expense.PaymentStatus toExpenseStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return Expense.PaymentStatus.UNPAID;
        if (paid.compareTo(total) >= 0) return Expense.PaymentStatus.PAID;
        return Expense.PaymentStatus.PARTIAL;
    }

    private Sale.PaymentStatus toSaleStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return Sale.PaymentStatus.UNPAID;
        if (paid.compareTo(total) >= 0) return Sale.PaymentStatus.PAID;
        return Sale.PaymentStatus.PARTIAL;
    }

    private Purchase.PaymentStatus toPurchaseStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return Purchase.PaymentStatus.UNPAID;
        if (paid.compareTo(total) >= 0) return Purchase.PaymentStatus.PAID;
        return Purchase.PaymentStatus.PARTIAL;
    }

    private String settlementStatus(BigDecimal paid, BigDecimal total) {
        if (paid.compareTo(BigDecimal.ZERO) <= 0) return "UNPAID";
        if (paid.compareTo(total) >= 0) return "PAID";
        return "PARTIAL";
    }

    private void saveHistory(Payment payment, PaymentApprovalHistory.ApprovalAction action,
                             String previousStatus, String newStatus, String comments, Long actedBy) {
        PaymentApprovalHistory history = new PaymentApprovalHistory();
        history.setPayment(payment);
        history.setAction(action);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(newStatus);
        history.setComments(comments);
        history.setActedBy(actedBy);
        history.setActedAt(LocalDateTime.now());
        historyRepository.save(history);
    }

    private void requirePaymentAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.FORBIDDEN, "Authenticated user not found"));
        Optional<Role> role = roleRepository.findById(user.getRoleId());
        String roleName = role.map(Role::getRoleName).orElse("").toUpperCase(Locale.ROOT);
        if (!roleName.equals("ADMIN") && !roleName.equals("SUPER_ADMIN")) {
            throw new ApiResponseException(HttpStatus.FORBIDDEN, "Only Admin or Super Admin can approve or reject payments");
        }
    }
    @Transactional
    @Auditable(action = "RETURN_FOR_CORRECTION", entityType = "Payment")
    public Payment returnForCorrection(Long id, String comments) {
        Long userId = authContextService.getCurrentUserId();
        requirePaymentAdmin(userId);
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
        if (payment.getApprovalStatus() != Payment.PaymentApprovalStatus.PENDING_APPROVAL) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only pending payment requests can be returned");
        }
        String old = payment.getApprovalStatus().name();
        payment.setApprovalStatus(Payment.PaymentApprovalStatus.RETURNED_FOR_CORRECTION);
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.PENDING);
        payment.setRejectionReason(safeComments(comments, "Returned for correction"));
        Payment saved = paymentRepository.save(payment);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.RETURNED_FOR_CORRECTION, old,
                saved.getApprovalStatus().name(), safeComments(comments, "Returned for correction"), userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "CANCEL", entityType = "Payment")
    public Payment cancelPayment(Long id, String reason) {
        Long userId = authContextService.getCurrentUserId();
        Payment payment = paymentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found with id: " + id));
        if (payment.getTransactionStatus() == Payment.PaymentTransactionStatus.POSTED) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Posted payments cannot be cancelled. Create a reversal request.");
        }
        String old = payment.getTransactionStatus().name();
        payment.setTransactionStatus(Payment.PaymentTransactionStatus.CANCELLED);
        payment.setPaymentStatus(Payment.PaymentStatus.UNPAID);
        payment.setVoidReason(safeComments(reason, "Payment cancelled"));
        payment.setVoidedBy(userId);
        payment.setVoidedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        saveHistory(saved, PaymentApprovalHistory.ApprovalAction.VOIDED, old,
                saved.getTransactionStatus().name(), safeComments(reason, "Payment cancelled"), userId);
        return saved;
    }

    @Transactional
    @Auditable(action = "REFUND", entityType = "Payment")
    public Payment createRefundRequest(Long originalPaymentId, BigDecimal refundAmount, Long accountId, String reason) {
        Payment original = paymentRepository.findById(originalPaymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Original payment not found"));
        if (original.getTransactionStatus() != Payment.PaymentTransactionStatus.POSTED) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only posted payments can be refunded");
        }
        BigDecimal amount = nonNull(refundAmount);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Refund amount must be greater than zero");
        }
        BigDecimal alreadyRefunded = paymentRepository.findByOriginalPaymentId(originalPaymentId).stream()
                .filter(p -> p.getTransactionStatus() == Payment.PaymentTransactionStatus.POSTED)
                .filter(p -> p.getPaymentType() == Payment.PaymentType.REFUND)
                .map(p -> nonNull(p.getApprovedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal refundable = nonNull(original.getApprovedAmount()).subtract(alreadyRefunded);
        if (amount.compareTo(refundable) > 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Refund amount exceeds refundable balance");
        }
        Payment refund = new Payment();
        refund.setOriginalPaymentId(originalPaymentId);
        refund.setDirection(Payment.PaymentDirection.REFUND);
        refund.setPaymentType(Payment.PaymentType.REFUND);
        refund.setPartyType(original.getPartyType());
        refund.setPartyId(original.getPartyId());
        refund.setReferenceId(original.getPaymentId());
        refund.setReferenceNo(original.getVoucherNo());
        refund.setAccountId(accountId != null ? accountId : original.getAccountId());
        refund.setPaymentMethod(original.getPaymentMethod());
        refund.setAmount(amount);
        refund.setRequestedAmount(amount);
        refund.setRefundReason(reason);
        refund.setNotes(safeComments(reason, "Refund request for " + original.getVoucherNo()));
        return createSubmittedPaymentRequest(refund);
    }

    @Transactional
    @Auditable(action = "REVERSE", entityType = "Payment")
    public Payment createReversalRequest(Long originalPaymentId, String reason) {
        Payment original = paymentRepository.findByIdForUpdate(originalPaymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Original payment not found"));
        if (original.getTransactionStatus() != Payment.PaymentTransactionStatus.POSTED) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Only posted payments can be reversed");
        }
        if (original.getReversalPaymentId() != null) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Payment already has a reversal request");
        }
        Payment reversal = new Payment();
        reversal.setOriginalPaymentId(originalPaymentId);
        reversal.setDirection(original.getDirection() == Payment.PaymentDirection.RECEIVE ? Payment.PaymentDirection.PAY : Payment.PaymentDirection.RECEIVE);
        reversal.setPaymentType(Payment.PaymentType.REFUND);
        reversal.setPartyType(original.getPartyType());
        reversal.setPartyId(original.getPartyId());
        reversal.setReferenceId(original.getPaymentId());
        reversal.setReferenceNo(original.getVoucherNo());
        reversal.setAccountId(original.getAccountId());
        reversal.setPaymentMethod(original.getPaymentMethod());
        reversal.setAmount(nonNull(original.getApprovedAmount()));
        reversal.setRequestedAmount(nonNull(original.getApprovedAmount()));
        reversal.setVoidReason(reason);
        reversal.setNotes(safeComments(reason, "Reversal request for " + original.getVoucherNo()));
        Payment saved = createSubmittedPaymentRequest(reversal);
        original.setReversalPaymentId(saved.getPaymentId());
        paymentRepository.save(original);
        return saved;
    }


    private void assignFinalVoucherNo(Payment payment) {
        if (payment.getPaymentId() == null || payment.getVoucherNo() == null || !payment.getVoucherNo().startsWith("TMP-PV-")) {
            return;
        }
        String prefix = switch (payment.getDirection()) {
            case RECEIVE -> "RCV";
            case PAY -> "PAY";
            case REFUND -> "REF";
            case TRANSFER -> "TRF";
        };
        String year = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
        payment.setVoucherNo(prefix + "-" + year + "-" + String.format("%06d", payment.getPaymentId()));
    }

    private String generateTemporaryVoucherNo() {
        return "TMP-PV-" + UUID.randomUUID();
    }

    private Payment.PaymentType parsePaymentType(String paymentTypeValue) {
        if (paymentTypeValue == null || paymentTypeValue.isBlank()) {
            return Payment.PaymentType.SALE;
        }
        try {
            return Payment.PaymentType.valueOf(paymentTypeValue);
        } catch (IllegalArgumentException ex) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Invalid payment type");
        }
    }

    private Payment.PartyType partyKindFor(Payment.PaymentType paymentType) {
        if (paymentType == Payment.PaymentType.EXPENSE) {
            return Payment.PartyType.VENDOR;
        }
        if (paymentType == Payment.PaymentType.PURCHASE || paymentType == Payment.PaymentType.SUPPLIER_ADVANCE) {
            return Payment.PartyType.SUPPLIER;
        }
        if (paymentType == Payment.PaymentType.ACCOUNT_TRANSFER) {
            return Payment.PartyType.INTERNAL;
        }
        return Payment.PartyType.CUSTOMER;
    }

    private Map<String, Object> partyRow(String partyType, Long id, String name, String code, String email, String phone, BigDecimal balance) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("partyType", partyType);
        row.put("partyId", id);
        row.put("title", defaultText(name, "N/A"));
        row.put("code", code);
        row.put("subtitle", String.join(" | ", List.of(defaultText(code, "-"), defaultText(email, "-"), defaultText(phone, "-"))));
        row.put("currentBalance", nonNull(balance));
        return row;
    }

    private Map<String, Object> referenceRow(PaymentAllocation.ReferenceType referenceType, Long referenceId, String referenceNo,
                                             Object documentDate, Long partyId, String partyName, BigDecimal totalAmount, Long currentPaymentId) {
        BigDecimal posted = allocationRepository.getPostedAmountForReferenceExcludingPayment(referenceType, referenceId, currentPaymentId);
        BigDecimal pending = allocationRepository.getPendingAmountForReferenceExcludingPayment(referenceType, referenceId, currentPaymentId);
        BigDecimal due = totalAmount.subtract(posted).subtract(pending).max(BigDecimal.ZERO);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("referenceType", referenceType.name());
        row.put("referenceId", referenceId);
        row.put("referenceNo", defaultText(referenceNo, "#" + referenceId));
        row.put("documentDate", documentDate);
        row.put("partyId", partyId);
        row.put("partyName", defaultText(partyName, "N/A"));
        row.put("totalAmount", totalAmount);
        row.put("postedPaidAmount", posted);
        row.put("pendingApprovalAmount", pending);
        row.put("dueAmount", due);
        return row;
    }

    private boolean hasDue(Map<String, Object> row) {
        Object due = row.get("dueAmount");
        return due instanceof BigDecimal amount && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal currentDueFor(PaymentAllocation.ReferenceType type, Long referenceId, Long currentPaymentId) {
        BigDecimal total = referenceTotal(type, referenceId);
        BigDecimal posted = allocationRepository.getPostedAmountForReferenceExcludingPayment(type, referenceId, currentPaymentId);
        BigDecimal pending = allocationRepository.getPendingAmountForReferenceExcludingPayment(type, referenceId, currentPaymentId);
        return total.subtract(posted).subtract(pending).max(BigDecimal.ZERO);
    }

    private BigDecimal referenceTotal(PaymentAllocation.ReferenceType type, Long referenceId) {
        return switch (type) {
            case EXPENSE -> {
                Expense expense = expenseRepository.findById(referenceId)
                        .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Expense reference not found"));
                if (expense.getStatus() != Expense.ExpenseStatus.APPROVED) {
                    throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only approved expenses can be paid");
                }
                yield nonNull(expense.getGrandTotal());
            }
            case SALE -> {
                Sale sale = saleRepository.findById(referenceId)
                        .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Sale reference not found"));
                if (sale.getStatus() == Sale.SaleStatus.CANCELLED) {
                    throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Cancelled sales cannot receive payment");
                }
                yield nonNull(sale.getTotalAmount());
            }
            case PURCHASE -> {
                Purchase purchase = purchaseRepository.findById(referenceId)
                        .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Purchase reference not found"));
                if (purchase.getStatus() != Purchase.PurchaseStatus.RECEIVED) {
                    throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only received purchases can be paid");
                }
                yield nonNull(purchase.getTotalAmount());
            }
            default -> BigDecimal.ZERO;
        };
    }

    private String referenceNo(PaymentAllocation.ReferenceType type, Long referenceId) {
        return switch (type) {
            case EXPENSE -> expenseRepository.findById(referenceId).map(Expense::getExpenseNo).orElse("#" + referenceId);
            case SALE -> saleRepository.findById(referenceId).map(Sale::getInvoiceNo).orElse("#" + referenceId);
            case PURCHASE -> purchaseRepository.findById(referenceId).map(Purchase::getPurchaseOrderNo).orElse("#" + referenceId);
            default -> "#" + referenceId;
        };
    }

    private String customerName(Long customerId) {
        return customerId == null ? null : customerRepository.findById(customerId).map(Customer::getCustomerName).orElse(null);
    }

    private String supplierName(Long supplierId) {
        return supplierId == null ? null : supplierRepository.findById(supplierId).map(Supplier::getSupplierName).orElse(null);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Page<Map<String, Object>> pageRows(List<Map<String, Object>> rows, Pageable pageable) {
        int start = (int) Math.min(pageable.getOffset(), rows.size());
        int end = Math.min(start + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(start, end), pageable, rows.size());
    }

    private Payment.PaymentMethod parsePaymentMethod(String method) {
        if (method == null || method.isBlank()) return Payment.PaymentMethod.CASH;
        try {
            return Payment.PaymentMethod.valueOf(method);
        } catch (IllegalArgumentException ignored) {
            return Payment.PaymentMethod.CASH;
        }
    }

    private BigDecimal normalizedRequestedAmount(Payment payment) {
        BigDecimal requested = nonNull(payment.getRequestedAmount());
        return requested.compareTo(BigDecimal.ZERO) > 0 ? requested : nonNull(payment.getAmount());
    }

    private BigDecimal nonNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeComments(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}


