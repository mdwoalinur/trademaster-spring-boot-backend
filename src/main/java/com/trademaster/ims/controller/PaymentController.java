package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.model.PaymentApprovalHistory;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "http://localhost:4200")
public class PaymentController {

    @Autowired private PaymentService paymentService;
    @Autowired private AuthContextService authContextService;

    @GetMapping


    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public Page<Payment> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());
        return paymentService.getPayments(pageable, type, status, search);
    }

    @GetMapping("/pending-approvals")


    @PreAuthorize("hasAuthority('PAYMENT_APPROVE')")
    public Page<Payment> getPendingApprovals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("submittedAt").descending());
        return paymentService.getPendingApprovals(pageable);
    }

    @GetMapping("/{id}")


    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<?> getPayment(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(paymentService.getPaymentById(id));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @GetMapping("/{id}/history")


    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentApprovalHistory>> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(id));
    }

    @GetMapping("/{id}/allocations")


    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<List<PaymentAllocation>> getAllocations(@PathVariable Long id) {
        return ResponseEntity.ok(paymentService.getAllocations(id));
    }

    @GetMapping("/parties")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<?> searchParties(
            @RequestParam String paymentType,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            return ResponseEntity.ok(paymentService.searchParties(paymentType, search, pageable));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @GetMapping("/outstanding-references")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<?> getOutstandingReferences(
            @RequestParam String paymentType,
            @RequestParam(required = false) Long partyId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Pageable pageable = PageRequest.of(page, size);
            return ResponseEntity.ok(paymentService.getOutstandingReferences(paymentType, partyId, search, pageable));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping


    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<?> createPayment(@RequestBody Payment payment,
                                           @RequestParam(defaultValue = "false") boolean submit) {
        try {
            payment.setReceivedBy(authContextService.getCurrentUserId());
            Payment created = submit
                    ? paymentService.createSubmittedPaymentRequest(payment)
                    : paymentService.createPayment(payment);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PutMapping("/{id}")


    @PreAuthorize("hasAuthority('PAYMENT_EDIT_DRAFT')")
    public ResponseEntity<?> updatePayment(@PathVariable Long id, @RequestBody Payment payment) {
        try {
            return ResponseEntity.ok(paymentService.updatePayment(id, payment));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/submit")


    @PreAuthorize("hasAuthority('PAYMENT_SUBMIT')")
    public ResponseEntity<?> submitPayment(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(paymentService.submitPayment(id));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/approve-and-post")


    @PreAuthorize("hasAuthority('PAYMENT_APPROVE') and hasAuthority('PAYMENT_POST')")
    public ResponseEntity<?> approveAndPost(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            BigDecimal approvedAmount = parseBigDecimal(payload != null ? payload.get("approvedAmount") : null);
            Long accountId = parseLong(payload != null ? payload.get("accountId") : null);
            Long destinationAccountId = parseLong(payload != null ? payload.get("destinationAccountId") : null);
            String comments = payload != null && payload.get("comments") != null ? payload.get("comments").toString() : null;
            return ResponseEntity.ok(paymentService.approveAndPost(id, approvedAmount, accountId, destinationAccountId, comments));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/reject")


    @PreAuthorize("hasAuthority('PAYMENT_REJECT')")
    public ResponseEntity<?> rejectPayment(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String reason = payload != null && payload.get("reason") != null ? payload.get("reason").toString() : null;
            return ResponseEntity.ok(paymentService.rejectPayment(id, reason));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @DeleteMapping("/{id}")


    @PreAuthorize("hasAuthority('PAYMENT_CANCEL')")
    public ResponseEntity<?> deletePayment(@PathVariable Long id) {
        try {
            paymentService.deletePayment(id);
            return ResponseEntity.noContent().build();
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/bulk")


    @PreAuthorize("hasAuthority('PAYMENT_CREATE')")
    public ResponseEntity<?> createBulkPayments(@RequestBody List<Payment> payments) {
        try {
            return new ResponseEntity<>(paymentService.createBulkPayments(payments), HttpStatus.CREATED);
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }
    @PostMapping("/{id}/return")
    @PreAuthorize("hasAuthority('PAYMENT_RETURN')")
    public ResponseEntity<?> returnForCorrection(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String comments = payload != null && payload.get("comments") != null ? payload.get("comments").toString() : null;
            return ResponseEntity.ok(paymentService.returnForCorrection(id, comments));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('PAYMENT_CANCEL')")
    public ResponseEntity<?> cancelPayment(@PathVariable Long id,
                                           @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String reason = payload != null && payload.get("reason") != null ? payload.get("reason").toString() : null;
            return ResponseEntity.ok(paymentService.cancelPayment(id, reason));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND')")
    public ResponseEntity<?> createRefund(@PathVariable Long id,
                                          @RequestBody(required = false) Map<String, Object> payload) {
        try {
            BigDecimal amount = parseBigDecimal(payload != null ? payload.get("amount") : null);
            Long accountId = parseLong(payload != null ? payload.get("accountId") : null);
            String reason = payload != null && payload.get("reason") != null ? payload.get("reason").toString() : null;
            return ResponseEntity.ok(paymentService.createRefundRequest(id, amount, accountId, reason));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/reversal")
    @PreAuthorize("hasAuthority('PAYMENT_VOID')")
    public ResponseEntity<?> createReversal(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            String reason = payload != null && payload.get("reason") != null ? payload.get("reason").toString() : null;
            return ResponseEntity.ok(paymentService.createReversalRequest(id, reason));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }


    private BigDecimal parseBigDecimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return new BigDecimal(value.toString());
    }

    private Long parseLong(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.valueOf(value.toString());
    }

    private ResponseEntity<Map<String, String>> error(ApiResponseException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}


