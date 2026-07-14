package com.trademaster.ims.controller;

import com.trademaster.ims.model.SaleReturn;
import com.trademaster.ims.model.SaleReturnItem;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.SaleReturnService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-returns")
@CrossOrigin(origins = "http://localhost:4200")
public class SaleReturnController {

    @Autowired
    private SaleReturnService returnService;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping
    public ResponseEntity<Page<SaleReturn>> getAllReturns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("returnDate").descending());
        Page<SaleReturn> result = returnService.getAllReturns(pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/sale/{saleId}")
    public ResponseEntity<List<SaleReturn>> getReturnsBySale(@PathVariable Long saleId) {
        return ResponseEntity.ok(returnService.getReturnsBySale(saleId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleReturn> getReturnById(@PathVariable Long id) {
        return ResponseEntity.ok(returnService.getReturnById(id));
    }

    
    @PostMapping
    public ResponseEntity<SaleReturn> createReturn(@RequestBody CreateReturnRequest request) {
        SaleReturn created = returnService.createReturn(request.saleReturn, request.items);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<SaleReturn> approveReturn(@PathVariable Long id, @RequestBody Map<String, Long> payload) {
        Long approvedBy = authContextService.getCurrentUserId();
        return ResponseEntity.ok(returnService.approveReturn(id, approvedBy));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectReturn(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        returnService.rejectReturn(id, payload.get("reason"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReturn(@PathVariable Long id) {
        returnService.deleteReturn(id);
        return ResponseEntity.noContent().build();
    }

    
    static class CreateReturnRequest {
        public SaleReturn saleReturn;
        public List<SaleReturnItem> items;
    }
}
