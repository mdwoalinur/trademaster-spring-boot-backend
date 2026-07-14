package com.trademaster.ims.controller;

import com.trademaster.ims.model.LowStockAlert;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.LowStockAlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/low-stock-alerts")
@CrossOrigin(origins = "http://localhost:4200")
public class LowStockAlertController {

    @Autowired
    private LowStockAlertService alertService;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping
    public List<LowStockAlert> getAllAlerts() {
        return alertService.getAllAlerts();
    }

    @GetMapping("/{id}")
    public ResponseEntity<LowStockAlert> getAlertById(@PathVariable Long id) {
        return ResponseEntity.ok(alertService.getAlertById(id));
    }

    @GetMapping("/product/{productId}")
    public List<LowStockAlert> getAlertsByProduct(@PathVariable Long productId) {
        return alertService.getAlertsByProduct(productId);
    }

    @GetMapping("/warehouse/{warehouseId}")
    public List<LowStockAlert> getAlertsByWarehouse(@PathVariable Long warehouseId) {
        return alertService.getAlertsByWarehouse(warehouseId);
    }

    @GetMapping("/unsent")
    public List<LowStockAlert> getUnsentAlerts() {
        return alertService.getUnsentAlerts();
    }

    @PostMapping("/generate")
    public ResponseEntity<Void> generateAlerts(@RequestBody Map<String, Long> payload) {
        Long companyId = authContextService.getCurrentCompanyId();
        alertService.generateAlerts(companyId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/mark-sent")
    public ResponseEntity<Void> markAlertAsSent(@PathVariable Long id) {
        alertService.markAlertAsSent(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mark-all-sent")
    public ResponseEntity<Void> markAllAlertsAsSent() {
        alertService.markAllAlertsAsSent();
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAlert(@PathVariable Long id) {
        alertService.deleteAlert(id);
        return ResponseEntity.noContent().build();
    }
}
