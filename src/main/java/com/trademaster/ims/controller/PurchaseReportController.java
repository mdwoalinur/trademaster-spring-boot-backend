
package com.trademaster.ims.controller;

import com.trademaster.ims.service.PurchaseReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "http://localhost:4200")
public class PurchaseReportController {

    @Autowired
    private PurchaseReportService purchaseReportService;

    @GetMapping("/purchases")
    public ResponseEntity<Map<String, Object>> getPurchaseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(purchaseReportService.getPurchaseReport(startDate, endDate));
    }
}