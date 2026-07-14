package com.trademaster.ims.controller;

import com.trademaster.ims.model.Sale;
import com.trademaster.ims.model.SaleItem;
import com.trademaster.ims.service.SaleService;
import com.trademaster.ims.service.SaleItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
@CrossOrigin(origins = "http://localhost:4200")
public class PosController {

    @Autowired
    private SaleService saleService;

    @Autowired
    private SaleItemService saleItemService;

    @PostMapping("/checkout")
    public ResponseEntity<Sale> checkout(@RequestBody PosCheckoutRequest request) {
        // 1. Save the sale
        Sale savedSale = saleService.createSale(request.getSale());

        // 2. Save all sale items with the sale ID
        for (SaleItem item : request.getItems()) {
            item.setSaleId(savedSale.getSaleId());
            saleItemService.createSaleItem(item);
        }

        return new ResponseEntity<>(savedSale, HttpStatus.CREATED);
    }
}

// Inner class for checkout request
class PosCheckoutRequest {
    private Sale sale;
    private List<SaleItem> items;

    public Sale getSale() {
        return sale;
    }

    public void setSale(Sale sale) {
        this.sale = sale;
    }

    public List<SaleItem> getItems() {
        return items;
    }

    public void setItems(List<SaleItem> items) {
        this.items = items;
    }
}