package com.trademaster.ims.controller;

import com.trademaster.ims.model.WastageRecord;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.WastageRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wastage-records")
@CrossOrigin(origins = "http://localhost:4200")
public class WastageRecordController {

    @Autowired
    private WastageRecordService recordService;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping
    public List<WastageRecord> getAllRecords() {
        return recordService.getAllRecords();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WastageRecord> getRecordById(@PathVariable Long id) {
        return ResponseEntity.ok(recordService.getRecordById(id));
    }

    @GetMapping("/product/{productId}")
    public List<WastageRecord> getRecordsByProduct(@PathVariable Long productId) {
        return recordService.getRecordsByProduct(productId);
    }

    @GetMapping("/warehouse/{warehouseId}")
    public List<WastageRecord> getRecordsByWarehouse(@PathVariable Long warehouseId) {
        return recordService.getRecordsByWarehouse(warehouseId);
    }

    @GetMapping("/status/{status}")
    public List<WastageRecord> getRecordsByStatus(@PathVariable WastageRecord.ApprovalStatus status) {
        return recordService.getRecordsByStatus(status);
    }

    @PostMapping
    public ResponseEntity<WastageRecord> createRecord(@RequestBody WastageRecord record) {
        WastageRecord created = recordService.createRecord(record);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WastageRecord> updateRecord(@PathVariable Long id, @RequestBody WastageRecord record) {
        WastageRecord updated = recordService.updateRecord(id, record);
        return ResponseEntity.ok(updated);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<WastageRecord> approveRecord(@PathVariable Long id, @RequestBody Map<String, Long> payload) {
        Long approvedBy = authContextService.getCurrentUserId();
        WastageRecord approved = recordService.approveRecord(id, approvedBy);
        return ResponseEntity.ok(approved);
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<WastageRecord> rejectRecord(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        String reason = payload.get("reason");
        WastageRecord rejected = recordService.rejectRecord(id, reason);
        return ResponseEntity.ok(rejected);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecord(@PathVariable Long id) {
        recordService.deleteRecord(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<List<WastageRecord>> createBulkRecords(@RequestBody List<WastageRecord> records) {
        List<WastageRecord> created = recordService.createBulkRecords(records);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
