package com.trademaster.ims.controller;

import com.trademaster.ims.model.StockTransfer;
import com.trademaster.ims.model.StockTransferItem;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.StockTransferService;
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
@RequestMapping("/api/stock-transfers")
@CrossOrigin(origins = "http://localhost:4200")
public class StockTransferController {

    @Autowired
    private StockTransferService transferService;

    @Autowired
    private AuthContextService authContextService;

    @GetMapping
    public Page<StockTransfer> getAllTransfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return transferService.getAllTransfers(pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockTransfer> getTransferById(@PathVariable Long id) {
        return ResponseEntity.ok(transferService.getTransferById(id));
    }

    @PostMapping
    public ResponseEntity<StockTransfer> createTransfer(
            @RequestBody TransferRequest request) {
        StockTransfer created = transferService.createTransfer(request.getTransfer(), request.getItems());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<StockTransfer> approveTransfer(
            @PathVariable Long id,
            @RequestBody Map<String, Long> payload) {
        Long approvedBy = authContextService.getCurrentUserId();
        return ResponseEntity.ok(transferService.approveTransfer(id, approvedBy));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectTransfer(@PathVariable Long id) {
        transferService.rejectTransfer(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelTransfer(@PathVariable Long id) {
        transferService.cancelTransfer(id);
        return ResponseEntity.noContent().build();
    }

    // Inner DTO (no separate file)
    static class TransferRequest {
        private StockTransfer transfer;
        private List<StockTransferItem> items;

        public StockTransfer getTransfer() { return transfer; }
        public void setTransfer(StockTransfer transfer) { this.transfer = transfer; }
        public List<StockTransferItem> getItems() { return items; }
        public void setItems(List<StockTransferItem> items) { this.items = items; }
    }
}
