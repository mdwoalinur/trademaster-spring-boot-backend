package com.trademaster.ims.service;

import com.trademaster.ims.model.StockMovement;
import com.trademaster.ims.repository.StockMovementRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StockMovementService {

    @Autowired
    private StockMovementRepository movementRepository;

    public List<StockMovement> getAllMovements() {
        return movementRepository.findAll();
    }

    public StockMovement getMovementById(Long id) {
        return movementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Stock movement not found"));
    }

    public List<StockMovement> getMovementsByProduct(Long productId) {
        return movementRepository.findByProductId(productId);
    }

    public List<StockMovement> getMovementsByWarehouse(Long warehouseId) {
        return movementRepository.findByWarehouseId(warehouseId);
    }

    public List<StockMovement> getMovementsByType(StockMovement.MovementType type) {
        return movementRepository.findByMovementType(type);
    }

    public List<StockMovement> getMovementsByDateRange(LocalDateTime start, LocalDateTime end) {
        return movementRepository.findByCreatedAtBetween(start, end);
    }

    @Transactional
    public StockMovement createMovement(StockMovement movement) {
        movement.setCreatedAt(LocalDateTime.now());
        return movementRepository.save(movement);
    }

    @Transactional
    public StockMovement recordMovement(Long productId, Long warehouseId, StockMovement.MovementType type,
                                        Integer quantity, Integer previousStock, Integer newStock,
                                        Long referenceId, String referenceNo, Long companyId, Long userId) {
        StockMovement movement = new StockMovement();
        movement.setCompanyId(companyId);
        movement.setProductId(productId);
        movement.setWarehouseId(warehouseId);
        movement.setMovementType(type);
        movement.setReferenceId(referenceId);
        movement.setReferenceNo(referenceNo);
        movement.setQuantity(quantity);
        movement.setPreviousStock(previousStock);
        movement.setNewStock(newStock);
        movement.setCreatedBy(userId);
        movement.setCreatedAt(LocalDateTime.now());
        return movementRepository.save(movement);
    }
}