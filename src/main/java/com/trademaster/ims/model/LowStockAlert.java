package com.trademaster.ims.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "low_stock_alerts")
@EntityListeners(AuditingEntityListener.class)
public class LowStockAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id")
    private Long alertId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "reorder_level")
    private Integer reorderLevel;

    @Column(name = "current_quantity")
    private Integer currentQuantity;

    @Column(name = "alert_sent")
    private Boolean alertSent = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ==================== Constructors ====================

    public LowStockAlert() {}

    public LowStockAlert(Long companyId, Long productId, Long warehouseId, 
                         Integer reorderLevel, Integer currentQuantity) {
        this.companyId = companyId;
        this.productId = productId;
        this.warehouseId = warehouseId;
        this.reorderLevel = reorderLevel;
        this.currentQuantity = currentQuantity;
        this.alertSent = false;
    }

    // ==================== Getters and Setters ====================

    public Long getAlertId() {
        return alertId;
    }

    public void setAlertId(Long alertId) {
        this.alertId = alertId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(Integer reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public Integer getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(Integer currentQuantity) {
        this.currentQuantity = currentQuantity;
    }

    public Boolean getAlertSent() {
        return alertSent;
    }

    public void setAlertSent(Boolean alertSent) {
        this.alertSent = alertSent;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // ==================== Helper Methods ====================

    /**
     * Marks the alert as sent and records the timestamp
     */
    public void markAsSent() {
        this.alertSent = true;
        this.sentAt = LocalDateTime.now();
    }

    /**
     * Checks if stock is critically low (below 50% of reorder level)
     */
    public boolean isCriticallyLow() {
        if (this.reorderLevel == null || this.currentQuantity == null) {
            return false;
        }
        int criticalThreshold = this.reorderLevel / 2;
        return this.currentQuantity <= criticalThreshold;
    }

    /**
     * Calculates shortage quantity needed to reach reorder level
     */
    public Integer getShortageQuantity() {
        if (this.reorderLevel == null || this.currentQuantity == null) {
            return 0;
        }
        int shortage = this.reorderLevel - this.currentQuantity;
        return shortage > 0 ? shortage : 0;
    }

    /**
     * Calculates shortage percentage
     */
    public Double getShortagePercentage() {
        if (this.reorderLevel == null || this.reorderLevel == 0 || this.currentQuantity == null) {
            return 0.0;
        }
        return ((double) (this.reorderLevel - this.currentQuantity) / this.reorderLevel) * 100;
    }
}