
package com.trademaster.ims.controller;

import com.trademaster.ims.service.TaxReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "http://localhost:4200")
public class TaxReportController {

    @Autowired
    private TaxReportService taxReportService;

    @GetMapping("/tax")
    public ResponseEntity<Map<String, Object>> getTaxReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(taxReportService.getTaxReport(startDate, endDate));
    }
}
