package com.trademaster.ims.controller;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trademaster.ims.model.ProductVariation;
import com.trademaster.ims.service.ProductVariationService;
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
@RequestMapping("/api/product-variations")
@CrossOrigin(origins = "http://localhost:4200")
public class ProductVariationController {

    private static final Logger log = LoggerFactory.getLogger(ProductVariationController.class);

    @Autowired
    private ProductVariationService productVariationService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @GetMapping
    public List<ProductVariation> getAllVariations() {
        return productVariationService.getAllVariations();
    }

    @GetMapping("/product/{productId}")
    public List<ProductVariation> getVariationsByProductId(@PathVariable Long productId) {
        return productVariationService.getVariationsByProductId(productId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductVariation> getVariationById(@PathVariable Long id) {
        return ResponseEntity.ok(productVariationService.getVariationById(id));
    }

    @PostMapping
    public ResponseEntity<ProductVariation> createVariation(@RequestBody ProductVariation variation) {
        ProductVariation created = productVariationService.createVariation(variation);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createVariationWithImage(
            @RequestPart("variation") MultipartFile variationPart,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        try {
            ProductVariation variation = parseVariationJson(readVariationPart(variationPart));
            ProductVariation created = productVariationService.createVariation(variation, image);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Product variation create failed", ex);
            return ResponseEntity.badRequest().body(Map.of("message", "Product variation create failed: " + ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductVariation> updateVariation(@PathVariable Long id, @RequestBody ProductVariation variation) {
        ProductVariation updated = productVariationService.updateVariation(id, variation);
        return ResponseEntity.ok(updated);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateVariationWithImage(
            @PathVariable Long id,
            @RequestPart("variation") MultipartFile variationPart,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        try {
            ProductVariation variation = parseVariationJson(readVariationPart(variationPart));
            ProductVariation updated = productVariationService.updateVariation(id, variation, image);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Variation not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Product variation update failed for id {}", id, ex);
            return ResponseEntity.badRequest().body(Map.of("message", "Product variation update failed: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVariation(@PathVariable Long id) {
        productVariationService.deleteVariation(id);
        return ResponseEntity.noContent().build();
    }

    private ProductVariation parseVariationJson(String variationJson) {
        if (variationJson == null || variationJson.isBlank()) {
            throw new IllegalArgumentException("Missing variation part");
        }
        try {
            return objectMapper.readValue(variationJson, ProductVariation.class);
        } catch (Exception ex) {
            log.error("Invalid variation JSON", ex);
            throw new IllegalArgumentException("Invalid variation JSON");
        }
    }

    private String readVariationPart(MultipartFile variationPart) {
        if (variationPart == null || variationPart.isEmpty()) {
            throw new IllegalArgumentException("Missing variation part");
        }
        try {
            return new String(variationPart.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Could not read variation multipart part", ex);
            throw new IllegalArgumentException("Could not read variation part");
        }
    }
}
