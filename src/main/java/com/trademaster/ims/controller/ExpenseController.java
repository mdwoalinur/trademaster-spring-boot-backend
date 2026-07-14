package com.trademaster.ims.controller;

import com.trademaster.ims.model.Expense;
import com.trademaster.ims.model.ExpenseAttachment;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
@CrossOrigin(origins = "http://localhost:4200")
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private AuthContextService authContextService;

    // ==================== Query Endpoints ====================
    @GetMapping
    public Page<Expense> getExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("expenseDate").descending());
        return expenseService.getExpenses(pageable, status, search);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpense(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpenseById(id));
    }

    // ==================== CRUD Endpoints ====================
    @PostMapping
    public ResponseEntity<Expense> createExpense(@RequestBody Expense expense) {
        expense.setCreatedBy(authContextService.getCurrentUserId());
        Expense created = expenseService.createExpense(expense);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Expense> updateExpense(@PathVariable Long id, @RequestBody Expense expense) {
        Expense updated = expenseService.updateExpense(id, expense);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id) {
        expenseService.deleteExpense(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== Approve / Reject Endpoints ====================
    @PutMapping("/{id}/approve")
    public ResponseEntity<Expense> approveExpense(@PathVariable Long id,
                                                  @RequestBody(required = false) Map<String, Long> payload) {
        Long approvedBy = authContextService.getCurrentUserId();
        Expense approved = expenseService.approveExpense(id, approvedBy);
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Expense> rejectExpense(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, String> payload) {
        Long rejectedBy = authContextService.getCurrentUserId();
        String reason = payload != null ? payload.get("reason") : null;
        Expense rejected = expenseService.rejectExpense(id, rejectedBy, reason);
        return ResponseEntity.ok(rejected);
    }

    @PostMapping("/{id}/request-payment")
    public ResponseEntity<?> requestPayment(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Payment payment = expenseService.requestPayment(id, payload);
            return new ResponseEntity<>(payment, HttpStatus.CREATED);
        } catch (ApiResponseException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/payment-summary")
    public ResponseEntity<?> getPaymentSummary(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(expenseService.getPaymentSummary(id));
        } catch (ApiResponseException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<PaymentAllocation>> getExpensePayments(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getExpensePayments(id));
    }

    // ==================== Attachment Endpoints ====================
    @PostMapping("/{id}/attachments")
    public ResponseEntity<?> addAttachment(@PathVariable Long id,
                                           @RequestParam("file") MultipartFile file) {
        try {
            ExpenseAttachment attachment = expenseService.addAttachment(id, file);
            return ResponseEntity.ok(attachment);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not upload file: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @GetMapping("/{id}/attachments")
    public ResponseEntity<List<ExpenseAttachment>> getAttachments(@PathVariable Long id) {
        return ResponseEntity.ok(expenseService.getAttachments(id));
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<?> deleteAttachment(@PathVariable Long attachmentId) {
        try {
            expenseService.deleteAttachment(attachmentId);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Could not delete file: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<List<Expense>> createBulkExpenses(@RequestBody List<Expense> expenses) {
        List<Expense> created = expenseService.createBulkExpenses(expenses);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
