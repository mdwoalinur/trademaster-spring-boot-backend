package com.trademaster.ims.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "stock_transfer_items")
public class StockTransferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_item_id")
    private Long transferItemId;

    @ManyToOne
    @JoinColumn(name = "transfer_id", nullable = false)
    @JsonIgnore
    private StockTransfer stockTransfer;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    // Getters and setters
    public Long getTransferItemId() { return transferItemId; }
    public void setTransferItemId(Long transferItemId) { this.transferItemId = transferItemId; }
    public StockTransfer getStockTransfer() { return stockTransfer; }
    public void setStockTransfer(StockTransfer stockTransfer) { this.stockTransfer = stockTransfer; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}