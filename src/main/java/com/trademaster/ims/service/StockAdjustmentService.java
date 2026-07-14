package com.trademaster.ims.service;

import com.trademaster.ims.model.Inventory;
import com.trademaster.ims.model.StockAdjustment;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.StockAdjustmentRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StockAdjustmentService {

    @Autowired
    private StockAdjustmentRepository adjustmentRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private StockMovementService movementService;

    @Autowired
    private AuthContextService authContextService;

    public List<StockAdjustment> getAllAdjustments() {
        return adjustmentRepository.findAll();
    }

    public StockAdjustment getAdjustmentById(Long id) {
        return adjustmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Adjustment not found with id: " + id));
    }

    public List<StockAdjustment> getAdjustmentsByProduct(Long productId) {
        return adjustmentRepository.findByProductId(productId);
    }

    public List<StockAdjustment> getAdjustmentsByWarehouse(Long warehouseId) {
        return adjustmentRepository.findByWarehouseId(warehouseId);
    }

    public List<StockAdjustment> getAdjustmentsByStatus(StockAdjustment.AdjustmentStatus status) {
        return adjustmentRepository.findByStatus(status);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "StockAdjustment")
    public StockAdjustment createAdjustment(StockAdjustment adjustment) {
        if (adjustment.getAdjustmentDate() == null) {
            adjustment.setAdjustmentDate(java.time.LocalDate.now());
        }
        adjustment.setCompanyId(authContextService.getCurrentCompanyId());
        adjustment.setCreatedAt(LocalDateTime.now());
        adjustment.setStatus(StockAdjustment.AdjustmentStatus.PENDING);
        return adjustmentRepository.save(adjustment);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "StockAdjustment")
    public StockAdjustment updateAdjustment(Long id, StockAdjustment adjustmentDetails) {
        StockAdjustment existing = getAdjustmentById(id);

        if (existing.getStatus() != StockAdjustment.AdjustmentStatus.PENDING) {
            throw new RuntimeException("Only pending adjustments can be updated");
        }

        // ✅ Update all fields that can change
        existing.setProductId(adjustmentDetails.getProductId());
        existing.setWarehouseId(adjustmentDetails.getWarehouseId());
        existing.setSystemQuantity(adjustmentDetails.getSystemQuantity());
        existing.setPhysicalQuantity(adjustmentDetails.getPhysicalQuantity());

        // ✅ Recalculate difference safely (avoid null pointer)
        Integer sysQty = existing.getSystemQuantity() != null ? existing.getSystemQuantity() : 0;
        Integer phyQty = existing.getPhysicalQuantity() != null ? existing.getPhysicalQuantity() : 0;
        existing.setDifference(phyQty - sysQty);

        existing.setReason(adjustmentDetails.getReason());
        existing.setNotes(adjustmentDetails.getNotes());

        // ✅ IMPORTANT: Set adjustmentDate if provided, otherwise keep existing or use today
        if (adjustmentDetails.getAdjustmentDate() != null) {
            existing.setAdjustmentDate(adjustmentDetails.getAdjustmentDate());
        } else if (existing.getAdjustmentDate() == null) {
            existing.setAdjustmentDate(java.time.LocalDate.now());
        }

        return adjustmentRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "APPROVE", entityType = "StockAdjustment")
    public StockAdjustment approveAdjustment(Long id, Long approvedBy) {
        StockAdjustment adjustment = getAdjustmentById(id);

        if (adjustment.getStatus() != StockAdjustment.AdjustmentStatus.PENDING) {
            throw new RuntimeException("Only pending adjustments can be approved");
        }

        adjustment.setStatus(StockAdjustment.AdjustmentStatus.APPROVED);
        adjustment.setApprovedBy(approvedBy);

        // Update inventory
        Inventory inv = inventoryService.updateStock(
                adjustment.getProductId(),
                adjustment.getWarehouseId(),
                adjustment.getPhysicalQuantity(),
                adjustment.getCompanyId()
        );

        // Record stock movement
        movementService.recordMovement(
                adjustment.getProductId(),
                adjustment.getWarehouseId(),
                com.trademaster.ims.model.StockMovement.MovementType.ADJUSTMENT,
                adjustment.getDifference(),
                adjustment.getSystemQuantity(),
                adjustment.getPhysicalQuantity(),
                adjustment.getAdjustmentId(),
                "ADJ-" + adjustment.getAdjustmentId(),
                adjustment.getCompanyId(),
                approvedBy
        );

        return adjustmentRepository.save(adjustment);
    }

    @Transactional
    @Auditable(action = "REJECT", entityType = "StockAdjustment")
    public StockAdjustment rejectAdjustment(Long id, String reason) {
        StockAdjustment adjustment = getAdjustmentById(id);
        if (adjustment.getStatus() != StockAdjustment.AdjustmentStatus.PENDING) {
            throw new RuntimeException("Only pending adjustments can be rejected");
        }
        adjustment.setStatus(StockAdjustment.AdjustmentStatus.REJECTED);
        adjustment.setNotes(reason);
        return adjustmentRepository.save(adjustment);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "StockAdjustment")
    public void deleteAdjustment(Long id) {
        StockAdjustment adjustment = getAdjustmentById(id);
        if (adjustment.getStatus() != StockAdjustment.AdjustmentStatus.PENDING) {
            throw new RuntimeException("Only pending adjustments can be deleted");
        }
        adjustmentRepository.deleteById(id);
    }
}
