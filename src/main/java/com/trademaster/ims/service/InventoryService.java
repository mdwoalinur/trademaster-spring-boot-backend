package com.trademaster.ims.service;

import com.trademaster.ims.model.Inventory;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.InventoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    // ==================== Query Methods ====================
    
    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public Inventory getInventoryById(Long id) {
        return inventoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inventory not found with id: " + id));
    }

    public List<Inventory> getInventoryByProduct(Long productId) {
        return inventoryRepository.findByProductId(productId);
    }

    public List<Inventory> getInventoryByWarehouse(Long warehouseId) {
        return inventoryRepository.findByWarehouseId(warehouseId);
    }
    
    public List<Inventory> getInventoryByCompany(Long companyId) {
        return inventoryRepository.findByCompanyId(companyId);
    }

    public Optional<Inventory> getInventoryByProductAndWarehouse(Long productId, Long warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId);
    }
    
    /**
     * Get current stock quantity for a product in a warehouse
     */
    public Integer getCurrentStock(Long productId, Long warehouseId) {
        return inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .map(Inventory::getAvailableQuantity)
                .orElse(0);
    }
    
    /**
     * Get total stock across all warehouses for a product
     */
    public Integer getTotalStock(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .stream()
                .mapToInt(inv -> inv.getAvailableQuantity() != null ? inv.getAvailableQuantity() : 0)
                .sum();
    }
    
    /**
     * Check if stock is low (below reorder level)
     */
    public boolean isLowStock(Long productId, Long warehouseId, Integer reorderLevel) {
        Integer currentStock = getCurrentStock(productId, warehouseId);
        return currentStock <= reorderLevel;
    }

    // ==================== Stock Management Methods ====================

    /**
     * Add stock to inventory (for purchase receive)
     */
    @Transactional
    public Inventory addStock(Long productId, Long warehouseId, Integer quantity, Long companyId) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }
        
        Inventory inv = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElse(new Inventory());
        
        if (inv.getInventoryId() == null) {
            // New inventory record
            inv.setCompanyId(companyId);
            inv.setProductId(productId);
            inv.setWarehouseId(warehouseId);
            inv.setQuantity(quantity);
            inv.setReservedQuantity(0);
        } else {
            // Existing inventory - add to quantity
            inv.setQuantity(inv.getQuantity() + quantity);
        }
        
        inv.setAvailableQuantity(inv.getQuantity() - 
                (inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0));
        inv.setLastUpdated(LocalDateTime.now());
        
        return inventoryRepository.save(inv);
    }

    /**
     * Remove stock from inventory (for sales)
     */
    @Transactional
    public Inventory removeStock(Long productId, Long warehouseId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }
        
        Inventory inv = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new RuntimeException("No inventory found for this product in the warehouse"));
        
        if (inv.getAvailableQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + inv.getAvailableQuantity() + 
                    ", Requested: " + quantity);
        }
        
        inv.setQuantity(inv.getQuantity() - quantity);
        inv.setAvailableQuantity(inv.getQuantity() - 
                (inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0));
        inv.setLastUpdated(LocalDateTime.now());
        
        return inventoryRepository.save(inv);
    }

    /**
     * Reserve stock (for pending orders)
     */
    @Transactional
    public Inventory reserveStock(Long productId, Long warehouseId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }
        
        Inventory inv = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new RuntimeException("No inventory found for this product in the warehouse"));
        
        if (inv.getAvailableQuantity() < quantity) {
            throw new RuntimeException("Insufficient stock to reserve. Available: " + 
                    inv.getAvailableQuantity() + ", Requested: " + quantity);
        }
        
        Integer currentReserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
        inv.setReservedQuantity(currentReserved + quantity);
        inv.setAvailableQuantity(inv.getQuantity() - inv.getReservedQuantity());
        inv.setLastUpdated(LocalDateTime.now());
        
        return inventoryRepository.save(inv);
    }

    /**
     * Release reserved stock (when order is cancelled)
     */
    @Transactional
    public Inventory releaseReservedStock(Long productId, Long warehouseId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }
        
        Inventory inv = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow(() -> new RuntimeException("No inventory found for this product in the warehouse"));
        
        Integer currentReserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0;
        if (currentReserved < quantity) {
            throw new RuntimeException("Cannot release more than reserved. Reserved: " + 
                    currentReserved + ", Requested: " + quantity);
        }
        
        inv.setReservedQuantity(currentReserved - quantity);
        inv.setAvailableQuantity(inv.getQuantity() - inv.getReservedQuantity());
        inv.setLastUpdated(LocalDateTime.now());
        
        return inventoryRepository.save(inv);
    }

    @Transactional
    public Inventory updateStock(Long productId, Long warehouseId, Integer newQuantity, Long companyId) {
        Inventory inv = inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElse(new Inventory());
        
        inv.setCompanyId(companyId);
        inv.setProductId(productId);
        inv.setWarehouseId(warehouseId);
        inv.setQuantity(newQuantity);
        inv.setReservedQuantity(inv.getReservedQuantity() != null ? inv.getReservedQuantity() : 0);
        inv.setAvailableQuantity(newQuantity - inv.getReservedQuantity());
        inv.setLastUpdated(LocalDateTime.now());
        
        return inventoryRepository.save(inv);
    }

    // ==================== CRUD Methods ====================

    @Transactional
    @Auditable(action = "CREATE", entityType = "Inventory")
    public Inventory createInventory(Inventory inventory) {
        inventory.setLastUpdated(LocalDateTime.now());
        inventory.updateAvailableQuantity();
        return inventoryRepository.save(inventory);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Inventory")
    public Inventory updateInventory(Long id, Inventory inventoryDetails) {
        Inventory existing = getInventoryById(id);
        existing.setQuantity(inventoryDetails.getQuantity());
        existing.setReservedQuantity(inventoryDetails.getReservedQuantity());
        existing.updateAvailableQuantity();
        existing.setLastUpdated(LocalDateTime.now());
        return inventoryRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Inventory")
    public void deleteInventory(Long id) {
        inventoryRepository.deleteById(id);
    }
}
