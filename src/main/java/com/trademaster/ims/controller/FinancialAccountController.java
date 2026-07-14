package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.FinancialAccount;
import com.trademaster.ims.service.FinancialAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/financial-accounts")
@CrossOrigin(origins = "http://localhost:4200")
public class FinancialAccountController {

    @Autowired private FinancialAccountService accountService;

    @GetMapping


    @PreAuthorize("hasAuthority('PAYMENT_ACCOUNT_VIEW')")
    public ResponseEntity<?> getAccounts(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("accountName").ascending());
        return ResponseEntity.ok(accountService.getAccounts(pageable, type, status, search));
    }

    @GetMapping("/{id}")


    @PreAuthorize("hasAuthority('PAYMENT_ACCOUNT_VIEW')")
    public ResponseEntity<?> getAccount(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(accountService.getAccount(id));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping


    @PreAuthorize("hasAuthority('PAYMENT_ACCOUNT_MANAGE')")
    public ResponseEntity<?> create(@RequestBody FinancialAccount account) {
        try {
            return new ResponseEntity<>(accountService.createAccount(account), HttpStatus.CREATED);
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PutMapping("/{id}")


    @PreAuthorize("hasAuthority('PAYMENT_ACCOUNT_MANAGE')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody FinancialAccount account) {
        try {
            return ResponseEntity.ok(accountService.updateAccount(id, account));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @PostMapping("/{id}/status")


    @PreAuthorize("hasAuthority('PAYMENT_ACCOUNT_MANAGE')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            FinancialAccount.AccountStatus status = FinancialAccount.AccountStatus.valueOf(payload.getOrDefault("status", "ACTIVE"));
            return ResponseEntity.ok(accountService.updateStatus(id, status));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @GetMapping("/{id}/statement")


    @PreAuthorize("hasAuthority('PAYMENT_LEDGER_VIEW')")
    public ResponseEntity<?> statement(@PathVariable Long id,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String startDate,
                                       @RequestParam(required = false) String endDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("postedAt").descending());
        LocalDate start = startDate != null && !startDate.isBlank() ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null && !endDate.isBlank() ? LocalDate.parse(endDate) : null;
        return ResponseEntity.ok(accountService.getStatement(id, start, end, pageable));
    }

    @DeleteMapping("/{id}")


    @PreAuthorize("hasAuthority('PAYMENT_ACCOUNT_MANAGE')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            accountService.deleteAccount(id);
            return ResponseEntity.noContent().build();
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    private ResponseEntity<Map<String, String>> error(ApiResponseException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}

