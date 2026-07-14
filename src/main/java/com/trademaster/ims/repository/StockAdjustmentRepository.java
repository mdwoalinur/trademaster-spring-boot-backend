package com.trademaster.ims.repository;

import com.trademaster.ims.model.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {
    
    // Find by status
    List<StockAdjustment> findByStatus(StockAdjustment.AdjustmentStatus status);
    
    // Find by product
    List<StockAdjustment> findByProductId(Long productId);
    
    // Find by warehouse
    List<StockAdjustment> findByWarehouseId(Long warehouseId);
    
    // Find by product and warehouse
    List<StockAdjustment> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    
    // Find by date range
    List<StockAdjustment> findByAdjustmentDateBetween(LocalDate startDate, LocalDate endDate);
    
    // Find pending adjustments by warehouse
    List<StockAdjustment> findByWarehouseIdAndStatus(Long warehouseId, StockAdjustment.AdjustmentStatus status);
}