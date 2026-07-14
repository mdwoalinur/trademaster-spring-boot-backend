package com.trademaster.ims.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_variations")
@EntityListeners(AuditingEntityListener.class)
public class ProductVariation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "variation_id")
    private Long variationId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variation_name", length = 100)
    private String variationName;

    @Column(name = "sku", nullable = false, unique = true, length = 100)
    private String sku;

    @Column(name = "buying_price", precision = 15, scale = 2)
    private BigDecimal buyingPrice = BigDecimal.ZERO;

    @Column(name = "additional_price", precision = 15, scale = 2)
    private BigDecimal additionalPrice = BigDecimal.ZERO;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "status")
    private Boolean status = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ==================== Constructors ====================

    public ProductVariation() {}

    public ProductVariation(Long productId, String variationName, String sku) {
        this.productId = productId;
        this.variationName = variationName;
        this.sku = sku;
        this.status = true;
        this.buyingPrice = BigDecimal.ZERO;
        this.additionalPrice = BigDecimal.ZERO;
    }

    // ==================== Getters and Setters ====================

    public Long getVariationId() {
        return variationId;
    }

    public void setVariationId(Long variationId) {
        this.variationId = variationId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getVariationName() {
        return variationName;
    }

    public void setVariationName(String variationName) {
        this.variationName = variationName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getBuyingPrice() {
        return buyingPrice;
    }

    public void setBuyingPrice(BigDecimal buyingPrice) {
        this.buyingPrice = buyingPrice;
    }

    public BigDecimal getAdditionalPrice() {
        return additionalPrice;
    }

    public void setAdditionalPrice(BigDecimal additionalPrice) {
        this.additionalPrice = additionalPrice;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if variation is active
     */
    public boolean isActive() {
        return this.status != null && this.status;
    }

    /**
     * Calculates final price (buyingPrice + additionalPrice)
     */
    public BigDecimal getFinalBuyingPrice() {
        BigDecimal buying = this.buyingPrice != null ? this.buyingPrice : BigDecimal.ZERO;
        BigDecimal additional = this.additionalPrice != null ? this.additionalPrice : BigDecimal.ZERO;
        return buying.add(additional);
    }

    /**
     * Returns display name with variation info
     */
    public String getDisplayName() {
        if (variationName != null && !variationName.isEmpty()) {
            return variationName + " (" + sku + ")";
        }
        return sku;
    }

    /**
     * Toggles status
     */
    public void toggleStatus() {
        this.status = !this.status;
    }
}