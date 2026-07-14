package com.trademaster.ims.controller;

import com.trademaster.ims.model.PartyLedgerEntry;
import com.trademaster.ims.service.PaymentReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payments/reports")
@CrossOrigin(origins = "http://localhost:4200")
@PreAuthorize("hasAuthority('PAYMENT_REPORT_VIEW')")
public class PaymentReportController {
    @Autowired private PaymentReportService reportService;

    @GetMapping("/register")
    public ResponseEntity<?> paymentRegister(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(required = false) String direction,
                                             @RequestParam(required = false) String method,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("paymentDate").descending());
        return ResponseEntity.ok(reportService.paymentRegister(startDate, endDate, type, direction, method, pageable));
    }

    @GetMapping("/account-statement")
    public ResponseEntity<?> accountStatement(@RequestParam Long accountId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("postedAt").descending());
        return ResponseEntity.ok(reportService.accountStatement(accountId, startDate, endDate, pageable));
    }

    @GetMapping("/party-ledger")
    public ResponseEntity<?> partyLedger(@RequestParam PartyLedgerEntry.PartyType partyType,
                                         @RequestParam Long partyId,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("postedAt").descending());
        return ResponseEntity.ok(reportService.partyStatement(partyType, partyId, startDate, endDate, pageable));
    }
}
