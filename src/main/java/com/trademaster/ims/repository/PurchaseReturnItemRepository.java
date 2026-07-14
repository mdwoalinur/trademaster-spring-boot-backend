package com.trademaster.ims.repository;

import com.trademaster.ims.model.PurchaseReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseReturnItemRepository extends JpaRepository<PurchaseReturnItem, Long> {
    List<PurchaseReturnItem> findByPurchaseReturn_Id(Long purchaseReturnId);

    @Query("SELECT COALESCE(SUM(i.quantity), 0) FROM PurchaseReturnItem i " +
            "WHERE i.purchaseItemId = :purchaseItemId AND i.purchaseReturn.status = 'CONFIRMED'")
    Integer getConfirmedReturnedQuantity(@Param("purchaseItemId") Long purchaseItemId);
}
