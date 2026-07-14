package com.trademaster.ims.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "purchase_items")
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_item_id")
    private Long purchaseItemId;

    @ManyToOne
    @JoinColumn(name = "purchase_id", nullable = false)
    @JsonIgnore
    private Purchase purchase;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ==================== Constructors ====================

    public PurchaseItem() {}

    public PurchaseItem(Long productId, Integer quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    // ==================== Calculated Fields ====================

    @Transient
    public BigDecimal getSubtotal() {
        if (quantity == null || unitPrice == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(quantity).multiply(unitPrice);
    }

    // ✅ ADDED: This method is called from PurchaseService
    public void calculateSubtotal() {
        // This method intentionally does nothing as subtotal is calculated dynamically
        // It exists for consistency with service layer calls
    }

    @Transient
    public BigDecimal getTaxAmount() {
        if (tax == null || tax.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal subtotal = getSubtotal();
        return subtotal.multiply(tax)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getDiscountAmount() {
        if (discount == null || discount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal subtotal = getSubtotal();
        return subtotal.multiply(discount)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getTotalPrice() {
        BigDecimal subtotal = getSubtotal();
        BigDecimal taxAmount = getTaxAmount();
        BigDecimal discountAmount = getDiscountAmount();
        return subtotal.add(taxAmount).subtract(discountAmount);
    }

    /**
     * Calculate all derived fields at once
     */
    public void calculateAllTotals() {
        // This method can be called to ensure all calculations are fresh
        // The getters will calculate on demand
    }

    // ==================== Getters and Setters ====================

    public Long getPurchaseItemId() {
        return purchaseItemId;
    }

    public void setPurchaseItemId(Long purchaseItemId) {
        this.purchaseItemId = purchaseItemId;
    }

    public Purchase getPurchase() {
        return purchase;
    }

    public void setPurchase(Purchase purchase) {
        this.purchase = purchase;
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
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // ==================== Helper Methods ====================

    /**
     * Check if this item has valid data
     */
    public boolean isValid() {
        return productId != null && productId > 0 
                && quantity != null && quantity > 0 
                && unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) >= 0;
    }

    @Override
    public String toString() {
        return "PurchaseItem{" +
                "purchaseItemId=" + purchaseItemId +
                ", productId=" + productId +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", subtotal=" + getSubtotal() +
                '}';
    }
}