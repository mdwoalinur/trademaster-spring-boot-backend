package com.trademaster.ims.controller;

import com.trademaster.ims.model.PurchaseReturn;
import com.trademaster.ims.service.PurchaseReturnService;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchase-returns")
@CrossOrigin(origins = "http://localhost:4200")
public class PurchaseReturnController {

    private final PurchaseReturnService service;

    public PurchaseReturnController(PurchaseReturnService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAll(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.getAll(status, search, startDate, endDate));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PostMapping
    public ResponseEntity<PurchaseReturn> create(@RequestBody PurchaseReturn purchaseReturn) {
        return new ResponseEntity<>(service.create(purchaseReturn), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PurchaseReturn> update(@PathVariable Long id, @RequestBody PurchaseReturn purchaseReturn) {
        return ResponseEntity.ok(service.update(id, purchaseReturn));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<PurchaseReturn> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(service.confirm(id));
    }

    @GetMapping("/by-purchase/{purchaseId}")
    public ResponseEntity<List<Map<String, Object>>> getByPurchase(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(service.getByPurchase(purchaseId));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleBusinessError(RuntimeException ex) {
        return buildErrorResponse(ex.getMessage());
    }

    @ExceptionHandler({DataIntegrityViolationException.class, DataAccessException.class})
    public ResponseEntity<Map<String, String>> handleDatabaseError(Exception ex) {
        return buildErrorResponse("Purchase return operation failed: " + rootMessage(ex));
    }

    private ResponseEntity<Map<String, String>> buildErrorResponse(String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("message", message == null || message.isBlank() ? "Purchase return operation failed" : message);
        return ResponseEntity.badRequest().body(body);
    }

    private String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() == null ? ex.getMessage() : root.getMessage();
    }
}
