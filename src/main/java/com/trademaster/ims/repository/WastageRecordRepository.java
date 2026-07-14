package com.trademaster.ims.repository;

import com.trademaster.ims.model.WastageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface WastageRecordRepository extends JpaRepository<WastageRecord, Long> {
    
    // Find by company
    List<WastageRecord> findByCompanyId(Long companyId);
    
    // Find by product
    List<WastageRecord> findByProductId(Long productId);
    
    // Find by warehouse
    List<WastageRecord> findByWarehouseId(Long warehouseId);
    
    // Find by status
    List<WastageRecord> findByStatus(WastageRecord.ApprovalStatus status);
    
    // Find by wastage date range
    List<WastageRecord> findByWastageDateBetween(LocalDate start, LocalDate end);
    
    // Find by product and date range
    List<WastageRecord> findByProductIdAndWastageDateBetween(Long productId, LocalDate start, LocalDate end);
    
    // Find by warehouse and status
    List<WastageRecord> findByWarehouseIdAndStatus(Long warehouseId, WastageRecord.ApprovalStatus status);
    
    // Find by wastage type
    List<WastageRecord> findByWastageType(WastageRecord.WastageType wastageType);
}