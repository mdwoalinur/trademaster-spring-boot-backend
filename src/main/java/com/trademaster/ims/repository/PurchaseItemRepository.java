package com.trademaster.ims.repository;

import com.trademaster.ims.model.PurchaseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseItemRepository extends JpaRepository<PurchaseItem, Long> {
    
    // Find items by purchase ID
    List<PurchaseItem> findByPurchase_PurchaseId(Long purchaseId);
    
    // Find items by product ID
    List<PurchaseItem> findByProductId(Long productId);
    
    // Delete items by purchase ID
    void deleteByPurchase_PurchaseId(Long purchaseId);
}