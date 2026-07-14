package com.trademaster.ims.controller;

import com.trademaster.ims.service.PaymentDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/payments/dashboard")
@CrossOrigin(origins = "http://localhost:4200")
@PreAuthorize("hasAuthority('PAYMENT_DASHBOARD_VIEW')")
public class PaymentDashboardController {
    @Autowired private PaymentDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<?> dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(dashboardService.dashboard(startDate, endDate));
    }
}
