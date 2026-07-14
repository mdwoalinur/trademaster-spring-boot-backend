package com.trademaster.ims.service;

import com.trademaster.ims.model.Expense;
import com.trademaster.ims.model.ExpenseAttachment;
import com.trademaster.ims.model.ExpenseItem;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.repository.ExpenseAttachmentRepository;
import com.trademaster.ims.repository.ExpenseItemRepository;
import com.trademaster.ims.repository.ExpenseRepository;
import com.trademaster.ims.repository.PaymentAllocationRepository;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.util.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ExpenseService {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ExpenseItemRepository itemRepository;
    @Autowired private ExpenseAttachmentRepository attachmentRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private AuthContextService authContextService;
    @Autowired private PaymentService paymentService;
    @Autowired private PaymentAllocationRepository paymentAllocationRepository;

    // ==================== Query Methods ====================
    public Page<Expense> getExpenses(Pageable pageable, String status, String search) {
        if (search != null && !search.isEmpty()) {
            return expenseRepository.search(search, pageable);
        } else if (status != null && !status.isEmpty()) {
            try {
                return expenseRepository.findByStatus(Expense.ExpenseStatus.valueOf(status), pageable);
            } catch (IllegalArgumentException e) {
                return expenseRepository.findAll(pageable);
            }
        }
        return expenseRepository.findAll(pageable);
    }

    /**
     * Fetch a single expense by its ID, including its line items and attachments.
     * Vendor details are not fetched here; the frontend uses a separate vendor map.
     */
    public Expense getExpenseById(Long id) {
        // Use the new method that joins items and attachments (attachments is now a Set)
        return expenseRepository.findExpenseWithItemsAndAttachments(id)
                .orElseThrow(() -> new RuntimeException("Expense not found with id: " + id));
    }

    // ==================== CRUD Methods ====================
    @Transactional
    @Auditable(action = "CREATE", entityType = "Expense")
    public Expense createExpense(Expense expense) {
        if (expenseRepository.existsByExpenseNo(expense.getExpenseNo())) {
            throw new RuntimeException("Expense number already exists");
        }
        calculateTotals(expense);
        expense.setPaymentStatus(Expense.PaymentStatus.UNPAID);
        for (ExpenseItem item : expense.getItems()) {
            item.setExpense(expense);
        }
        return expenseRepository.save(expense);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Expense")
    public Expense updateExpense(Long id, Expense expenseData) {
        Expense existing = getExpenseById(id);
        // Allow editing only when status is DRAFT or SUBMITTED (not APPROVED/REJECTED)
        if (existing.getStatus() == Expense.ExpenseStatus.APPROVED || existing.getStatus() == Expense.ExpenseStatus.REJECTED) {
            throw new RuntimeException("Approved or rejected expenses cannot be modified");
        }
        existing.setExpenseDate(expenseData.getExpenseDate());
        existing.setVendorId(expenseData.getVendorId());
        existing.setPaymentMethod(expenseData.getPaymentMethod());
        existing.setReferenceNo(expenseData.getReferenceNo());
        existing.setNotes(expenseData.getNotes());
        // Clear and replace items
        existing.getItems().clear();
        for (ExpenseItem item : expenseData.getItems()) {
            item.setExpense(existing);
            existing.getItems().add(item);
        }
        calculateTotals(existing);
        existing.setPaymentStatus(Expense.PaymentStatus.UNPAID);
        return expenseRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "REQUEST_PAYMENT", entityType = "Expense")
    public Payment requestPayment(Long expenseId, Map<String, Object> payload) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Expense not found with id: " + expenseId));
        if (expense.getStatus() != Expense.ExpenseStatus.APPROVED) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only approved expenses can have payment requests");
        }

        BigDecimal posted = paymentAllocationRepository.getPostedAmountForReference(PaymentAllocation.ReferenceType.EXPENSE, expenseId);
        BigDecimal due = expense.getGrandTotal().subtract(posted);
        if (due.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Expense is already fully paid");
        }

        BigDecimal requested = payload != null && payload.get("requestedAmount") != null
                ? new BigDecimal(payload.get("requestedAmount").toString())
                : due;
        if (requested.compareTo(BigDecimal.ZERO) <= 0 || requested.compareTo(due) > 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Requested amount must be greater than zero and not exceed due amount");
        }

        Payment payment = new Payment();
        payment.setDirection(Payment.PaymentDirection.PAY);
        payment.setPaymentType(Payment.PaymentType.EXPENSE);
        payment.setPartyType(Payment.PartyType.VENDOR);
        payment.setPartyId(expense.getVendorId());
        payment.setReferenceId(expense.getExpenseId());
        payment.setReferenceNo(expense.getExpenseNo());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setAmount(requested);
        payment.setRequestedAmount(requested);
        payment.setPaymentMethod(parsePaymentMethod(payload != null ? payload.get("paymentMethod") : null, expense.getPaymentMethod()));
        payment.setNotes(payload != null && payload.get("notes") != null
                ? payload.get("notes").toString()
                : "Expense payment request awaiting admin approval");
        return paymentService.createSubmittedPaymentRequest(payment);
    }

    public Map<String, Object> getPaymentSummary(Long expenseId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Expense not found with id: " + expenseId));
        return paymentService.getReferencePaymentSummary(
                PaymentAllocation.ReferenceType.EXPENSE,
                expenseId,
                expense.getGrandTotal());
    }

    public List<PaymentAllocation> getExpensePayments(Long expenseId) {
        return paymentAllocationRepository.findByReferenceTypeAndReferenceId(PaymentAllocation.ReferenceType.EXPENSE, expenseId);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Expense")
    public void deleteExpense(Long id) {
        // Delete physical files for all attachments
        List<ExpenseAttachment> attachments = attachmentRepository.findByExpense_ExpenseId(id);
        for (ExpenseAttachment att : attachments) {
            try {
                fileStorageService.deleteFile(att.getFileName());
            } catch (IOException e) {
                // Log error but continue with deletion of other attachments and the expense
                System.err.println("Failed to delete file: " + att.getFileName());
            }
        }
        expenseRepository.deleteById(id);
    }

    // ==================== Business Logic: Approve/Reject ====================
    @Transactional
    @Auditable(action = "APPROVE", entityType = "Expense")
    public Expense approveExpense(Long id, Long approvedBy) {
        Expense expense = getExpenseById(id);
        if (expense.getStatus() != Expense.ExpenseStatus.SUBMITTED) {
            throw new RuntimeException("Only submitted expenses can be approved");
        }
        expense.setStatus(Expense.ExpenseStatus.APPROVED);
        expense.setApprovedBy(approvedBy);
        return expenseRepository.save(expense);
    }

    @Transactional
    @Auditable(action = "REJECT", entityType = "Expense")
    public Expense rejectExpense(Long id, Long rejectedBy, String reason) {
        Expense expense = getExpenseById(id);
        if (expense.getStatus() != Expense.ExpenseStatus.SUBMITTED) {
            throw new RuntimeException("Only submitted expenses can be rejected");
        }
        expense.setStatus(Expense.ExpenseStatus.REJECTED);
        expense.setApprovedBy(rejectedBy);
        String existingNotes = expense.getNotes() != null ? expense.getNotes() : "";
        expense.setNotes(existingNotes + "\n[Rejected]: " + reason);
        return expenseRepository.save(expense);
    }

    // ==================== Attachment Methods ====================
    @Transactional
    public ExpenseAttachment addAttachment(Long expenseId, MultipartFile file) throws IOException {
        Expense expense = getExpenseById(expenseId);
        String storedName = fileStorageService.storeFile(file);
        ExpenseAttachment attachment = new ExpenseAttachment();
        attachment.setExpense(expense);
        attachment.setFileName(storedName);
        attachment.setOriginalName(file.getOriginalFilename());
        attachment.setFileSize(file.getSize());
        attachment.setFileType(file.getContentType());
        attachment.setFilePath("/api/files/receipts/" + storedName);
        attachment.setUploadedAt(LocalDateTime.now());
        return attachmentRepository.save(attachment);
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) throws IOException {
        ExpenseAttachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Attachment not found"));
        fileStorageService.deleteFile(attachment.getFileName());
        attachmentRepository.delete(attachment);
    }

    public List<ExpenseAttachment> getAttachments(Long expenseId) {
        return attachmentRepository.findByExpense_ExpenseId(expenseId);
    }

    // ==================== Helper: Calculate Totals ====================
    private void calculateTotals(Expense expense) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        for (ExpenseItem item : expense.getItems()) {
            BigDecimal itemSubtotal = item.getSubtotal();
            // Discount calculation
            BigDecimal discount = itemSubtotal.multiply(item.getDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            item.setDiscountAmount(discount);
            BigDecimal afterDiscount = itemSubtotal.subtract(discount);
            // Tax calculation
            BigDecimal tax = afterDiscount.multiply(item.getTaxRate())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            item.setTaxAmount(tax);
            // Total for line
            item.setTotalPrice(afterDiscount.add(tax));
            // Accumulate
            subtotal = subtotal.add(itemSubtotal);
            totalTax = totalTax.add(tax);
        }
        expense.setTotalAmount(subtotal);
        expense.setTaxAmount(totalTax);
        expense.setGrandTotal(subtotal.add(totalTax).subtract(expense.getDiscountAmount() != null ? expense.getDiscountAmount() : BigDecimal.ZERO));
    }
    
    @Transactional
    public List<Expense> createBulkExpenses(List<Expense> expenses) {
        List<Expense> createdExpenses = new ArrayList<>();
        for (Expense expense : expenses) {
            expense.setCreatedBy(authContextService.getCurrentUserId());
            createdExpenses.add(createExpense(expense));
        }
        return createdExpenses;
    }

    private Payment.PaymentMethod parsePaymentMethod(Object requestedMethod, Expense.PaymentMethod fallback) {
        String method = requestedMethod != null ? requestedMethod.toString() : fallback.name();
        try {
            return Payment.PaymentMethod.valueOf(method);
        } catch (IllegalArgumentException ignored) {
            return Payment.PaymentMethod.CASH;
        }
    }
}
