package com.trademaster.ims.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "inventory", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"warehouse_id", "product_id"})
})
@EntityListeners(AuditingEntityListener.class)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventory_id")
    private Long inventoryId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "reserved_quantity")
    private Integer reservedQuantity = 0;

    @Column(name = "available_quantity")
    private Integer availableQuantity = 0;

    @LastModifiedDate
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    // ==================== Constructors ====================

    public Inventory() {}

    public Inventory(Long companyId, Long warehouseId, Long productId, Integer quantity) {
        this.companyId = companyId;
        this.warehouseId = warehouseId;
        this.productId = productId;
        this.quantity = quantity;
        this.reservedQuantity = 0;
        this.updateAvailableQuantity();
    }

    // ==================== Getters and Setters ====================

    public Long getInventoryId() {
        return inventoryId;
    }

    public void setInventoryId(Long inventoryId) {
        this.inventoryId = inventoryId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
        this.updateAvailableQuantity();
    }

    public Integer getReservedQuantity() {
        return reservedQuantity;
    }

    public void setReservedQuantity(Integer reservedQuantity) {
        this.reservedQuantity = reservedQuantity;
        this.updateAvailableQuantity();
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    // ==================== Helper Methods ====================

    /**
     * Updates availableQuantity based on quantity - reservedQuantity
     */
    public void updateAvailableQuantity() {
        int qty = this.quantity != null ? this.quantity : 0;
        int reserved = this.reservedQuantity != null ? this.reservedQuantity : 0;
        this.availableQuantity = qty - reserved;
    }

    /**
     * Increases stock by given amount
     */
    public void addStock(Integer amount) {
        if (amount != null && amount > 0) {
            this.quantity += amount;
            this.updateAvailableQuantity();
        }
    }

    /**
     * Decreases stock by given amount (for sales)
     */
    public void removeStock(Integer amount) {
        if (amount != null && amount > 0 && this.quantity >= amount) {
            this.quantity -= amount;
            this.updateAvailableQuantity();
        }
    }

    /**
     * Reserves stock for pending orders
     */
    public void reserveStock(Integer amount) {
        if (amount != null && amount > 0 && this.availableQuantity >= amount) {
            this.reservedQuantity += amount;
            this.updateAvailableQuantity();
        }
    }

    /**
     * Releases reserved stock
     */
    public void releaseReservedStock(Integer amount) {
        if (amount != null && amount > 0 && this.reservedQuantity >= amount) {
            this.reservedQuantity -= amount;
            this.updateAvailableQuantity();
        }
    }

    /**
     * Checks if stock is below reorder level
     */
    public boolean isLowStock(Integer reorderLevel) {
        return this.availableQuantity <= reorderLevel;
    }
}