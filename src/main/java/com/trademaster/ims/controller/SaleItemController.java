package com.trademaster.ims.controller;

import com.trademaster.ims.model.SaleItem;
import com.trademaster.ims.service.SaleItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sale-items")
@CrossOrigin(origins = "http://localhost:4200")
public class SaleItemController {

    @Autowired
    private SaleItemService saleItemService;

    @GetMapping
    public List<SaleItem> getAllSaleItems() {
        return saleItemService.getAllSaleItems();
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleItem> getSaleItemById(@PathVariable Long id) {
        return ResponseEntity.ok(saleItemService.getSaleItemById(id));
    }

    @GetMapping("/by-sale/{saleId}")
    public List<SaleItem> getSaleItemsBySaleId(@PathVariable Long saleId) {
        return saleItemService.getSaleItemsBySaleId(saleId);
    }

    @GetMapping("/by-product/{productId}")
    public List<SaleItem> getSaleItemsByProductId(@PathVariable Long productId) {
        return saleItemService.getSaleItemsByProductId(productId);
    }

    @PostMapping
    public ResponseEntity<SaleItem> createSaleItem(@RequestBody SaleItem saleItem) {
        SaleItem created = saleItemService.createSaleItem(saleItem);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SaleItem> updateSaleItem(@PathVariable Long id, @RequestBody SaleItem saleItem) {
        SaleItem updated = saleItemService.updateSaleItem(id, saleItem);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSaleItem(@PathVariable Long id) {
        saleItemService.deleteSaleItem(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<List<SaleItem>> createBulkSaleItems(@RequestBody List<SaleItem> saleItems) {
        List<SaleItem> created = saleItemService.createBulkSaleItems(saleItems);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
    
}