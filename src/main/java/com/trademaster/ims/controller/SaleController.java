package com.trademaster.ims.controller;

import com.trademaster.ims.model.Sale;
import com.trademaster.ims.service.SaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
@CrossOrigin(origins = "http://localhost:4200")
public class SaleController {

    @Autowired
    private SaleService saleService;

    @GetMapping
    public List<Sale> getAllSales() {
        return saleService.getAllSales();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sale> getSaleById(@PathVariable Long id) {
        return ResponseEntity.ok(saleService.getSaleById(id));
    }

    @PostMapping
    public ResponseEntity<Sale> createSale(@RequestBody Sale sale) {
        Sale created = saleService.createSale(sale);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Sale> updateSale(@PathVariable Long id, @RequestBody Sale sale) {
        Sale updated = saleService.updateSale(id, sale);
        return ResponseEntity.ok(updated);
    }
    
    @PostMapping("/pos-checkout")
    public ResponseEntity<?> posCheckout(@RequestBody Map<String, Object> request) {
        try {
            Sale created = saleService.createPosSale(request);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", ex.getMessage() == null ? "POS checkout failed" : ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable Long id) {
        saleService.deleteSale(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<List<Sale>> createBulkSales(@RequestBody List<Sale> sales) {
        List<Sale> created = saleService.createBulkSales(sales);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
