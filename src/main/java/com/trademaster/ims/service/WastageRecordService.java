package com.trademaster.ims.service;

import com.trademaster.ims.model.Inventory;
import com.trademaster.ims.model.WastageRecord;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.WastageRecordRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WastageRecordService {

    @Autowired
    private WastageRecordRepository recordRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockMovementService movementService;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private NotificationService notificationService;

    public List<WastageRecord> getAllRecords() {
        return recordRepository.findAll();
    }

    public WastageRecord getRecordById(Long id) {
        return recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Wastage record not found"));
    }

    public List<WastageRecord> getRecordsByProduct(Long productId) {
        return recordRepository.findByProductId(productId);
    }

    public List<WastageRecord> getRecordsByWarehouse(Long warehouseId) {
        return recordRepository.findByWarehouseId(warehouseId);
    }

    public List<WastageRecord> getRecordsByStatus(WastageRecord.ApprovalStatus status) {
        return recordRepository.findByStatus(status);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "WastageRecord")
    public WastageRecord createRecord(WastageRecord record) {
        record.setCompanyId(authContextService.getCurrentCompanyId());
        record.setCreatedBy(authContextService.getCurrentUserId());
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        record.setStatus(WastageRecord.ApprovalStatus.PENDING);
        WastageRecord saved = recordRepository.save(record);
        notificationService.createWastageNotification(saved.getCompanyId(), saved.getCreatedBy(), saved.getWastageId(),
                "Wastage Record Created", "Wastage record #" + saved.getWastageId() + " has been created and is pending approval.");
        return saved;
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "WastageRecord")
    public WastageRecord updateRecord(Long id, WastageRecord recordDetails) {
        WastageRecord existing = getRecordById(id);
        
        if (existing.getStatus() != WastageRecord.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only pending records can be updated");
        }
        
        existing.setWastageType(recordDetails.getWastageType());
        existing.setQuantity(recordDetails.getQuantity());
        existing.setWastageDate(recordDetails.getWastageDate());
        existing.setReason(recordDetails.getReason());
        existing.setBatchNo(recordDetails.getBatchNo());
        existing.setManufacturingDate(recordDetails.getManufacturingDate());
        existing.setExpiryDate(recordDetails.getExpiryDate());
        existing.setFinancialLoss(recordDetails.getFinancialLoss());
        existing.setRecoveryAmount(recordDetails.getRecoveryAmount());
        existing.setNotes(recordDetails.getNotes());
        existing.setUpdatedAt(LocalDateTime.now());
        return recordRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "APPROVE", entityType = "WastageRecord")
    public WastageRecord approveRecord(Long id, Long approvedBy) {
        WastageRecord record = getRecordById(id);
        
        if (record.getStatus() != WastageRecord.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only pending records can be approved");
        }
        
        record.setStatus(WastageRecord.ApprovalStatus.APPROVED);
        record.setApprovedBy(approvedBy);
        record.setUpdatedAt(LocalDateTime.now());

        // Get current inventory for product and warehouse
        Optional<Inventory> invOpt = inventoryService.getInventoryByProductAndWarehouse(
                record.getProductId(), 
                record.getWarehouseId()
        );
        
        if (invOpt.isPresent()) {
            Inventory inv = invOpt.get();
            int newQuantity = inv.getQuantity() - record.getQuantity().intValue();
            
            // Update inventory: decrease stock by wastage quantity
            inventoryService.updateStock(
                    record.getProductId(),
                    record.getWarehouseId(),
                    newQuantity,
                    record.getCompanyId()
            );

            // Record stock movement
            movementService.recordMovement(
                    record.getProductId(),
                    record.getWarehouseId(),
                    com.trademaster.ims.model.StockMovement.MovementType.ADJUSTMENT,
                    -record.getQuantity().intValue(),
                    inv.getQuantity(),
                    newQuantity,
                    record.getWastageId(),
                    "WASTE-" + record.getWastageId(),
                    record.getCompanyId(),
                    approvedBy
            );
        }

        WastageRecord saved = recordRepository.save(record);
        notificationService.createWastageNotification(saved.getCompanyId(), approvedBy, saved.getWastageId(),
                "Wastage Record Approved", "Wastage record #" + saved.getWastageId() + " has been approved and stock adjusted.");
        return saved;
    }

    @Transactional
    @Auditable(action = "REJECT", entityType = "WastageRecord")
    public WastageRecord rejectRecord(Long id, String reason) {
        WastageRecord record = getRecordById(id);
        
        if (record.getStatus() != WastageRecord.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only pending records can be rejected");
        }
        
        record.setStatus(WastageRecord.ApprovalStatus.REJECTED);
        record.setNotes(reason);
        record.setUpdatedAt(LocalDateTime.now());
        return recordRepository.save(record);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "WastageRecord")
    public void deleteRecord(Long id) {
        WastageRecord record = getRecordById(id);
        if (record.getStatus() != WastageRecord.ApprovalStatus.PENDING) {
            throw new RuntimeException("Only pending records can be deleted");
        }
        recordRepository.deleteById(id);
    }
    
    @Transactional
    public List<WastageRecord> createBulkRecords(List<WastageRecord> records) {
        List<WastageRecord> createdRecords = new ArrayList<>();
        for (WastageRecord record : records) {
            // createRecord মেথডে status, createdAt, updatedAt অটোmatic সেট হচ্ছে
            createdRecords.add(createRecord(record));
        }
        return createdRecords;
    }
}
