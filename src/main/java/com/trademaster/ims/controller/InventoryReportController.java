
package com.trademaster.ims.controller;

import com.trademaster.ims.service.InventoryReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "http://localhost:4200")
public class InventoryReportController {

    @Autowired
    private InventoryReportService inventoryReportService;

    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> getInventoryReport() {
        return ResponseEntity.ok(inventoryReportService.getInventoryReport());
    }
}