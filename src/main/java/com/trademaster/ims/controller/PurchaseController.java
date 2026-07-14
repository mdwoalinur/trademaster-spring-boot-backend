package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.service.PurchaseReturnService;
import com.trademaster.ims.service.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchases")
@CrossOrigin(origins = "http://localhost:4200")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private PurchaseReturnService purchaseReturnService;

    @GetMapping
    public ResponseEntity<Page<Purchase>> getPurchases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("purchaseDate").descending());
        return ResponseEntity.ok(purchaseService.getPurchases(pageable, status, search));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Purchase> getPurchase(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.getPurchaseById(id));
    }

    @GetMapping("/{purchaseId}/returnable-items")
    public ResponseEntity<List<Map<String, Object>>> getReturnableItems(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(purchaseReturnService.getReturnableItems(purchaseId));
    }
    
    @GetMapping("/order-no/{orderNo}")
    public ResponseEntity<Purchase> getPurchaseByOrderNo(@PathVariable String orderNo) {
        return ResponseEntity.ok(purchaseService.getPurchaseByOrderNo(orderNo));
    }

    @PostMapping
    public ResponseEntity<Purchase> createPurchase(@RequestBody Purchase purchase) {
        Purchase created = purchaseService.createPurchase(purchase);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Purchase> updatePurchase(@PathVariable Long id, @RequestBody Purchase purchase) {
        // ✅ Only allow update if status is PENDING
        return ResponseEntity.ok(purchaseService.updatePurchase(id, purchase));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePurchase(@PathVariable Long id) {
        purchaseService.deletePurchase(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/receive")
    public ResponseEntity<?> markAsReceived(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        try {
            String deliveryDateStr = payload.get("deliveryDate");
            String notes = payload.get("notes");
            
            // ✅ Handle both ISO datetime and simple date formats
            LocalDate deliveryDate;
            try {
                if (deliveryDateStr == null || deliveryDateStr.isEmpty()) {
                    return ResponseEntity.badRequest().body("Delivery date is required");
                }
                
                if (deliveryDateStr.contains("T")) {
                    // ISO format: "2026-04-19T00:00:00.000Z" -> extract date part
                    deliveryDate = LocalDate.parse(deliveryDateStr.substring(0, 10));
                } else {
                    // Simple format: "2026-04-19"
                    deliveryDate = LocalDate.parse(deliveryDateStr);
                }
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body("Invalid date format. Expected YYYY-MM-DD");
            }
            
            Purchase received = purchaseService.markAsReceived(id, deliveryDate, notes);
            return ResponseEntity.ok(received);
            
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelPurchase(@PathVariable Long id) {
        try {
            purchaseService.cancelPurchase(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/request-payment")
    public ResponseEntity<?> requestPayment(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Payment payment = purchaseService.requestPayment(id, payload);
            return new ResponseEntity<>(payment, HttpStatus.CREATED);
        } catch (ApiResponseException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/payment-summary")
    public ResponseEntity<?> getPaymentSummary(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(purchaseService.getPaymentSummary(id));
        } catch (ApiResponseException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<PaymentAllocation>> getPurchasePayments(@PathVariable Long id) {
        return ResponseEntity.ok(purchaseService.getPurchasePayments(id));
    }
    
    @GetMapping("/supplier/{supplierId}")
    public ResponseEntity<Page<Purchase>> getPurchasesBySupplier(
            @PathVariable Long supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("purchaseDate").descending());
        return ResponseEntity.ok(purchaseService.getPurchasesBySupplier(supplierId, pageable));
    }
    
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<Page<Purchase>> getPurchasesByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("purchaseDate").descending());
        return ResponseEntity.ok(purchaseService.getPurchasesByWarehouse(warehouseId, pageable));
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<List<Purchase>> createBulkPurchases(@RequestBody List<Purchase> purchases) {
        List<Purchase> created = purchaseService.createBulkPurchases(purchases);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
