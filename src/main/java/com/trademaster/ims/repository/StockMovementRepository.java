package com.trademaster.ims.repository;

import com.trademaster.ims.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    
    // Find by product
    List<StockMovement> findByProductId(Long productId);
    
    // Find by warehouse
    List<StockMovement> findByWarehouseId(Long warehouseId);
    
    // Find by movement type
    List<StockMovement> findByMovementType(StockMovement.MovementType movementType);
    
    // Find by reference ID and type
    List<StockMovement> findByReferenceIdAndMovementType(Long referenceId, StockMovement.MovementType movementType);
    
    // Find by date range
    List<StockMovement> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    // Find by product and date range
    List<StockMovement> findByProductIdAndCreatedAtBetween(Long productId, LocalDateTime start, LocalDateTime end);
    
    // Find by warehouse and movement type
    List<StockMovement> findByWarehouseIdAndMovementType(Long warehouseId, StockMovement.MovementType movementType);
}