package com.trademaster.ims.controller;

import com.trademaster.ims.model.StockAdjustment;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.StockAdjustmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-adjustments")
@CrossOrigin(origins = "http://localhost:4200")
public class StockAdjustmentController {

    @Autowired
    private StockAdjustmentService adjustmentService;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping
    public List<StockAdjustment> getAllAdjustments() {
        return adjustmentService.getAllAdjustments();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockAdjustment> getAdjustmentById(@PathVariable Long id) {
        return ResponseEntity.ok(adjustmentService.getAdjustmentById(id));
    }

    @GetMapping("/product/{productId}")
    public List<StockAdjustment> getAdjustmentsByProduct(@PathVariable Long productId) {
        return adjustmentService.getAdjustmentsByProduct(productId);
    }

    @GetMapping("/warehouse/{warehouseId}")
    public List<StockAdjustment> getAdjustmentsByWarehouse(@PathVariable Long warehouseId) {
        return adjustmentService.getAdjustmentsByWarehouse(warehouseId);
    }

    @GetMapping("/status/{status}")
    public List<StockAdjustment> getAdjustmentsByStatus(@PathVariable String status) {
        // ✅ Convert String to Enum
        StockAdjustment.AdjustmentStatus adjustmentStatus = StockAdjustment.AdjustmentStatus.valueOf(status.toUpperCase());
        return adjustmentService.getAdjustmentsByStatus(adjustmentStatus);
    }

    @PostMapping
    public ResponseEntity<StockAdjustment> createAdjustment(@RequestBody StockAdjustment adjustment) {
        StockAdjustment created = adjustmentService.createAdjustment(adjustment);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<StockAdjustment> updateAdjustment(@PathVariable Long id, @RequestBody StockAdjustment adjustment) {
        StockAdjustment updated = adjustmentService.updateAdjustment(id, adjustment);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<StockAdjustment> approveAdjustment(@PathVariable Long id, @RequestBody Map<String, Long> payload) {
        Long approvedBy = authContextService.getCurrentUserId();
        StockAdjustment approved = adjustmentService.approveAdjustment(id, approvedBy);
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<StockAdjustment> rejectAdjustment(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String reason = payload.get("reason");
        StockAdjustment rejected = adjustmentService.rejectAdjustment(id, reason);
        return ResponseEntity.ok(rejected);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAdjustment(@PathVariable Long id) {
        adjustmentService.deleteAdjustment(id);
        return ResponseEntity.noContent().build();
    }
}
