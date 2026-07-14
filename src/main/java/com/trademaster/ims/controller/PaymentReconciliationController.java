package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.service.PaymentReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/reconciliation")
@CrossOrigin(origins = "http://localhost:4200")
@PreAuthorize("hasAuthority('PAYMENT_RECONCILE')")
public class PaymentReconciliationController {
    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationController.class);

    @Autowired private PaymentReconciliationService reconciliationService;

    @GetMapping("/statement-entries")
    public ResponseEntity<?> list(@RequestParam(required = false) Long accountId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        try {
            logReconciliationAuthority("statement-entries");
            Pageable pageable = PageRequest.of(page, size, Sort.by("transactionDate").descending());
            return ResponseEntity.ok(reconciliationService.list(accountId, status, pageable));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importCsv(@RequestParam Long accountId, @RequestParam("file") MultipartFile file) {
        try {
            logReconciliationAuthority("import");
            return ResponseEntity.ok(reconciliationService.importCsv(accountId, file));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{statementEntryId}/match")
    public ResponseEntity<?> match(@PathVariable Long statementEntryId, @RequestBody Map<String, Object> payload) {
        try {
            logReconciliationAuthority("match");
            Long paymentId = Long.valueOf(payload.get("paymentId").toString());
            return ResponseEntity.ok(reconciliationService.match(statementEntryId, paymentId));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{statementEntryId}/reconcile")
    public ResponseEntity<?> reconcile(@PathVariable Long statementEntryId, @RequestBody(required = false) Map<String, Object> payload) {
        try {
            logReconciliationAuthority("reconcile");
            String comments = payload != null && payload.get("comments") != null ? payload.get("comments").toString() : null;
            return ResponseEntity.ok(reconciliationService.reconcile(statementEntryId, comments));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{statementEntryId}/unmatch")
    public ResponseEntity<?> unmatch(@PathVariable Long statementEntryId) {
        try {
            logReconciliationAuthority("unmatch");
            return ResponseEntity.ok(reconciliationService.unmatch(statementEntryId));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary() {
        logReconciliationAuthority("summary");
        return ResponseEntity.ok(reconciliationService.summary());
    }

    private void logReconciliationAuthority(String action) {
        if (!log.isDebugEnabled()) {
            return;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.debug("Payment reconciliation request action={}, username={}, authorities={}",
                action,
                authentication != null ? authentication.getName() : "anonymous",
                authentication != null ? authentication.getAuthorities() : "none");
    }

    private ResponseEntity<Map<String, String>> error(ApiResponseException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}
