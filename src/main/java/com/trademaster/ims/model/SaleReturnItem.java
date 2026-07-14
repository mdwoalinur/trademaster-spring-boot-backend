package com.trademaster.ims.model;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_return_items")
@EntityListeners(AuditingEntityListener.class)
public class SaleReturnItem {

    public enum ItemCondition {
        GOOD, DAMAGED, EXPIRED
    }

    public enum ActionTaken {
        REFUND, EXCHANGE, STORE_CREDIT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_item_id")
    private Long returnItemId;

    @Column(name = "return_id")
    private Long returnId;

    @Column(name = "sales_item_id")
    private Long salesItemId;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "returned_quantity")
    private Integer returnedQuantity;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "refund_amount")
    private BigDecimal refundAmount;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition")
    private ItemCondition itemCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_taken")
    private ActionTaken actionTaken;

    @CreatedDate
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // ==================== Getters and Setters ====================

	public Long getReturnItemId() {
		return returnItemId;
	}

	public void setReturnItemId(Long returnItemId) {
		this.returnItemId = returnItemId;
	}

	public Long getReturnId() {
		return returnId;
	}

	public void setReturnId(Long returnId) {
		this.returnId = returnId;
	}

	public Long getSalesItemId() {
		return salesItemId;
	}

	public void setSalesItemId(Long salesItemId) {
		this.salesItemId = salesItemId;
	}

	public Long getProductId() {
		return productId;
	}

	public void setProductId(Long productId) {
		this.productId = productId;
	}

	public Integer getReturnedQuantity() {
		return returnedQuantity;
	}

	public void setReturnedQuantity(Integer returnedQuantity) {
		this.returnedQuantity = returnedQuantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public BigDecimal getRefundAmount() {
		return refundAmount;
	}

	public void setRefundAmount(BigDecimal refundAmount) {
		this.refundAmount = refundAmount;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public ItemCondition getItemCondition() {
		return itemCondition;
	}

	public void setItemCondition(ItemCondition itemCondition) {
		this.itemCondition = itemCondition;
	}

	public ActionTaken getActionTaken() {
		return actionTaken;
	}

	public void setActionTaken(ActionTaken actionTaken) {
		this.actionTaken = actionTaken;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

   
   
}