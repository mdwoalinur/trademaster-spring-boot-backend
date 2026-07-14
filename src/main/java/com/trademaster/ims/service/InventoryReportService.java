
package com.trademaster.ims.service;

import com.trademaster.ims.model.Inventory;
import com.trademaster.ims.model.Product;
import com.trademaster.ims.repository.InventoryRepository;
import com.trademaster.ims.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryReportService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    public Map<String, Object> getInventoryReport() {
        List<Inventory> inventories = inventoryRepository.findAll();
        List<Product> products = productRepository.findAll();

        // Map productId -> Product for quick lookup
        Map<Long, Product> productMap = products.stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // Total stock quantity
        int totalStockQuantity = inventories.stream()
            .mapToInt(inv -> inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0)
            .sum();

        // Total inventory value (quantity * buyingPrice)
        BigDecimal totalInventoryValue = BigDecimal.ZERO;
        for (Inventory inv : inventories) {
            Product product = productMap.get(inv.getProductId());
            if (product != null && product.getBuyingPrice() != null) {
                int qty = inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0;
                totalInventoryValue = totalInventoryValue.add(
                    product.getBuyingPrice().multiply(BigDecimal.valueOf(qty))
                );
            }
        }

        // Low stock items (quantity <= reorderLevel)
        List<Map<String, Object>> lowStockItems = new ArrayList<>();
        for (Inventory inv : inventories) {
            Product product = productMap.get(inv.getProductId());
            if (product != null && product.getReorderLevel() != null) {
                int available = inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0;
                if (available <= product.getReorderLevel()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("productId", product.getId());
                    item.put("productName", product.getProductName());
                    item.put("sku", product.getSku());
                    item.put("imageUrl", product.getImageUrl());
                    item.put("productImageUrl", product.getImageUrl());
                    item.put("currentStock", available);
                    item.put("reorderLevel", product.getReorderLevel());
                    item.put("unitPrice", product.getBuyingPrice());
                    item.put("totalValue", product.getBuyingPrice().multiply(BigDecimal.valueOf(available)));
                    lowStockItems.add(item);
                }
            }
        }

        // All items with stock info (for table)
        List<Map<String, Object>> allItems = new ArrayList<>();
        for (Inventory inv : inventories) {
            Product product = productMap.get(inv.getProductId());
            if (product != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("productId", product.getId());
                item.put("productName", product.getProductName());
                item.put("sku", product.getSku());
                item.put("imageUrl", product.getImageUrl());
                item.put("productImageUrl", product.getImageUrl());
                item.put("categoryId", product.getCategoryId());
                item.put("unitType", product.getSelectUnit());
                item.put("currentStock", inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0);
                item.put("unitPrice", product.getBuyingPrice());
                item.put("totalValue", product.getBuyingPrice().multiply(
                    BigDecimal.valueOf(inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0)
                ));
                item.put("reorderLevel", product.getReorderLevel());
                item.put("status", (inv.getAvailableQuantity() != null && inv.getAvailableQuantity() <= product.getReorderLevel()) ? "LOW" : "NORMAL");
                allItems.add(item);
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("totalProducts", products.size());
        report.put("totalStockQuantity", totalStockQuantity);
        report.put("totalInventoryValue", totalInventoryValue);
        report.put("lowStockItems", lowStockItems);
        report.put("allItems", allItems);
        return report;
    }
}
