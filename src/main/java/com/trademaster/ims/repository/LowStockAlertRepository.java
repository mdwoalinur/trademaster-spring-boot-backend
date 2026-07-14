package com.trademaster.ims.repository;

import com.trademaster.ims.model.LowStockAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LowStockAlertRepository extends JpaRepository<LowStockAlert, Long> {
    
    // Find unsent alerts
    List<LowStockAlert> findByAlertSentFalse();
    
    // Find by alert sent status
    List<LowStockAlert> findByAlertSent(Boolean alertSent);
    
    // Find by product
    List<LowStockAlert> findByProductId(Long productId);
    
    // Find by warehouse
    List<LowStockAlert> findByWarehouseId(Long warehouseId);
    
    // Find by company
    List<LowStockAlert> findByCompanyId(Long companyId);
    
    // Find unsent alerts by product
    List<LowStockAlert> findByProductIdAndAlertSentFalse(Long productId);
    
    // Find unsent alerts by warehouse
    List<LowStockAlert> findByWarehouseIdAndAlertSentFalse(Long warehouseId);
    
    // Delete sent alerts older than specified date
    void deleteByAlertSentTrueAndSentAtBefore(java.time.LocalDateTime date);
}