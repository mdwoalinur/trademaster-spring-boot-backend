package com.trademaster.ims.service;

import com.trademaster.ims.model.*;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.*;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class StockTransferService {

    @Autowired private StockTransferRepository transferRepository;
    @Autowired private StockTransferItemRepository itemRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private StockMovementService movementService;
    @Autowired private AuthContextService authContextService;

    public Page<StockTransfer> getAllTransfers(Pageable pageable) {
        return transferRepository.findAll(pageable);
    }

    public StockTransfer getTransferById(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfer not found with id: " + id));
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "StockTransfer")
    public StockTransfer createTransfer(StockTransfer transfer, List<StockTransferItem> items) {
        transfer.setTransferNo("TRF-" + System.currentTimeMillis());
        transfer.setStatus(StockTransfer.TransferStatus.PENDING);
        transfer.setCreatedBy(authContextService.getCurrentUserId());
        transfer.setCreatedAt(LocalDateTime.now());
        transfer.setUpdatedAt(LocalDateTime.now());

        StockTransfer saved = transferRepository.save(transfer);
        for (StockTransferItem item : items) {
            item.setStockTransfer(saved);
            itemRepository.save(item);
        }
        return saved;
    }

    @Transactional
    @Auditable(action = "APPROVE", entityType = "StockTransfer")
    public StockTransfer approveTransfer(Long transferId, Long approvedBy) {
        StockTransfer transfer = getTransferById(transferId);
        if (transfer.getStatus() != StockTransfer.TransferStatus.PENDING) {
            throw new RuntimeException("Only pending transfers can be approved");
        }

        Long companyId = authContextService.getCurrentCompanyId();
        List<StockTransferItem> items = itemRepository.findByStockTransfer_TransferId(transferId);

        for (StockTransferItem item : items) {
            // 1. Remove stock from source warehouse
            inventoryService.removeStock(item.getProductId(), transfer.getFromWarehouseId(), item.getQuantity());
            // 2. Add stock to destination warehouse
            inventoryService.addStock(item.getProductId(), transfer.getToWarehouseId(), item.getQuantity(), companyId);

            // 3. Record stock movements (out from source)
            movementService.recordMovement(
                item.getProductId(),
                transfer.getFromWarehouseId(),
                StockMovement.MovementType.TRANSFER,
                -item.getQuantity(),
                null, null,
                transfer.getTransferId(),
                transfer.getTransferNo(),
                companyId,
                approvedBy
            );
            // 4. Record stock movement (in to destination)
            movementService.recordMovement(
                item.getProductId(),
                transfer.getToWarehouseId(),
                StockMovement.MovementType.TRANSFER,
                item.getQuantity(),
                null, null,
                transfer.getTransferId(),
                transfer.getTransferNo(),
                companyId,
                approvedBy
            );
        }

        transfer.setStatus(StockTransfer.TransferStatus.APPROVED);
        transfer.setApprovedBy(approvedBy);
        transfer.setApprovedAt(LocalDateTime.now());
        transfer.setUpdatedAt(LocalDateTime.now());
        return transferRepository.save(transfer);
    }

    @Transactional
    @Auditable(action = "REJECT", entityType = "StockTransfer")
    public void rejectTransfer(Long transferId) {
        StockTransfer transfer = getTransferById(transferId);
        if (transfer.getStatus() != StockTransfer.TransferStatus.PENDING) {
            throw new RuntimeException("Only pending transfers can be rejected");
        }
        transfer.setStatus(StockTransfer.TransferStatus.REJECTED);
        transfer.setUpdatedAt(LocalDateTime.now());
        transferRepository.save(transfer);
    }

    @Transactional
    @Auditable(action = "CANCEL", entityType = "StockTransfer")
    public void cancelTransfer(Long transferId) {
        StockTransfer transfer = getTransferById(transferId);
        if (transfer.getStatus() != StockTransfer.TransferStatus.PENDING) {
            throw new RuntimeException("Only pending transfers can be cancelled");
        }
        transfer.setStatus(StockTransfer.TransferStatus.CANCELLED);
        transfer.setUpdatedAt(LocalDateTime.now());
        transferRepository.save(transfer);
    }
}
