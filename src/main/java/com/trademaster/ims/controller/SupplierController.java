package com.trademaster.ims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trademaster.ims.model.Supplier;
import com.trademaster.ims.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/suppliers")
@CrossOrigin(origins = "http://localhost:4200") // Allow Angular frontend
public class SupplierController {

    private static final Logger log = LoggerFactory.getLogger(SupplierController.class);

    @Autowired
    private SupplierService supplierService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @GetMapping
    public List<Supplier> getAllSuppliers() {
        return supplierService.getAllSuppliers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Supplier> getSupplierById(@PathVariable Long id) {
        return ResponseEntity.ok(supplierService.getSupplierById(id));
    }

    @PostMapping
    public ResponseEntity<Supplier> createSupplier(@RequestBody Supplier supplier) {
        Supplier created = supplierService.createSupplier(supplier);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createSupplierWithPhoto(
            @RequestPart("supplier") MultipartFile supplierPart,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        try {
            Supplier supplier = parseSupplierJson(readSupplierPart(supplierPart));
            Supplier created = supplierService.createSupplier(supplier, photo);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Supplier create failed", ex);
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier create failed: " + ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Supplier> updateSupplier(@PathVariable Long id, @RequestBody Supplier supplier) {
        Supplier updated = supplierService.updateSupplier(id, supplier);
        return ResponseEntity.ok(updated);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateSupplierWithPhoto(
            @PathVariable Long id,
            @RequestPart("supplier") MultipartFile supplierPart,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        try {
            Supplier supplier = parseSupplierJson(readSupplierPart(supplierPart));
            Supplier updated = supplierService.updateSupplier(id, supplier, photo);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Supplier not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Supplier update failed for id {}", id, ex);
            return ResponseEntity.badRequest().body(Map.of("message", "Supplier update failed: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSupplier(@PathVariable Long id) {
        supplierService.deleteSupplier(id);
        return ResponseEntity.noContent().build();
    }

    private Supplier parseSupplierJson(String supplierJson) {
        if (supplierJson == null || supplierJson.isBlank()) {
            throw new IllegalArgumentException("Missing supplier part");
        }
        try {
            return objectMapper.readValue(supplierJson, Supplier.class);
        } catch (Exception ex) {
            log.error("Invalid supplier JSON", ex);
            throw new IllegalArgumentException("Invalid supplier JSON");
        }
    }

    private String readSupplierPart(MultipartFile supplierPart) {
        if (supplierPart == null || supplierPart.isEmpty()) {
            throw new IllegalArgumentException("Missing supplier part");
        }
        try {
            return new String(supplierPart.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Could not read supplier multipart part", ex);
            throw new IllegalArgumentException("Could not read supplier part");
        }
    }
}
