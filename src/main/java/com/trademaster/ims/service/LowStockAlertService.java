package com.trademaster.ims.service;

import com.trademaster.ims.model.Inventory;
import com.trademaster.ims.model.LowStockAlert;
import com.trademaster.ims.model.Product;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.LowStockAlertRepository;
import com.trademaster.ims.repository.InventoryRepository;
import com.trademaster.ims.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LowStockAlertService {

    @Autowired
    private LowStockAlertRepository alertRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ProductRepository productRepository;

    public List<LowStockAlert> getAllAlerts() {
        return alertRepository.findAll();
    }

    public LowStockAlert getAlertById(Long id) {
        return alertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
    }

    public List<LowStockAlert> getAlertsByProduct(Long productId) {
        return alertRepository.findByProductId(productId);
    }

    public List<LowStockAlert> getAlertsByWarehouse(Long warehouseId) {
        return alertRepository.findByWarehouseId(warehouseId);
    }

    public List<LowStockAlert> getUnsentAlerts() {
        return alertRepository.findByAlertSent(false);
    }

    @Transactional
    public void generateAlerts(Long companyId) {
        List<Inventory> inventories = inventoryRepository.findByCompanyId(companyId);
        for (Inventory inv : inventories) {
            Product product = productRepository.findById(inv.getProductId()).orElse(null);
            if (product != null && product.getReorderLevel() != null) {
                if (inv.getQuantity() <= product.getReorderLevel()) {
                    // Check if alert already exists and not sent
                    List<LowStockAlert> existing = alertRepository.findByProductId(inv.getProductId());
                    boolean already = existing.stream().anyMatch(a -> !a.getAlertSent());
                    if (!already) {
                        LowStockAlert alert = new LowStockAlert();
                        alert.setCompanyId(companyId);
                        alert.setProductId(inv.getProductId());
                        alert.setWarehouseId(inv.getWarehouseId());
                        alert.setReorderLevel(product.getReorderLevel());
                        alert.setCurrentQuantity(inv.getQuantity());
                        alert.setAlertSent(false);
                        alertRepository.save(alert);
                    }
                }
            }
        }
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "LowStockAlert")
    public void markAlertAsSent(Long id) {
        LowStockAlert alert = getAlertById(id);
        alert.setAlertSent(true);
        alert.setSentAt(LocalDateTime.now());
        alertRepository.save(alert);
    }

    @Transactional
    public void markAllAlertsAsSent() {
        List<LowStockAlert> unsentAlerts = alertRepository.findByAlertSent(false);
        for (LowStockAlert alert : unsentAlerts) {
            alert.setAlertSent(true);
            alert.setSentAt(LocalDateTime.now());
            alertRepository.save(alert);
        }
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "LowStockAlert")
    public void deleteAlert(Long id) {
        alertRepository.deleteById(id);
    }
}
