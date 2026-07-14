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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SaleReturnService {

    @Autowired private SaleReturnRepository returnRepository;
    @Autowired private SaleReturnItemRepository returnItemRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private InventoryService inventoryService;
    @Autowired private StockMovementService movementService;
    @Autowired private AuthContextService authContextService;

    public Page<SaleReturn> getAllReturns(Pageable pageable) {
        return returnRepository.findAll(pageable);
    }

    public SaleReturn getReturnById(Long id) {
        return returnRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Return not found with id: " + id));
    }

    public List<SaleReturn> getReturnsBySale(Long saleId) {
        return returnRepository.findBySaleId(saleId);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "SaleReturn")
    public SaleReturn createReturn(SaleReturn saleReturn, List<SaleReturnItem> items) {
        Sale sale = saleRepository.findById(saleReturn.getSaleId())
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        String returnNo = "RET-" + System.currentTimeMillis();
        saleReturn.setReturnNo(returnNo);
        // ✅ IMPORTANT: Set invoiceNo from original sale
        saleReturn.setInvoiceNo(sale.getInvoiceNo());
        saleReturn.setCustomerId(sale.getCustomerId());
        saleReturn.setWarehouseId(sale.getWarehouseId());
        saleReturn.setCreatedBy(authContextService.getCurrentUserId());
        saleReturn.setStatus(SaleReturn.ReturnStatus.PENDING);
        if (saleReturn.getCreatedAt() == null) {
            saleReturn.setCreatedAt(LocalDateTime.now());
        }
        saleReturn.setUpdatedAt(LocalDateTime.now());

        SaleReturn savedReturn = returnRepository.save(saleReturn);

        for (SaleReturnItem item : items) {
            item.setReturnId(savedReturn.getReturnId());
            returnItemRepository.save(item);
        }

        return savedReturn;
    }

    @Transactional
    @Auditable(action = "APPROVE", entityType = "SaleReturn")
    public SaleReturn approveReturn(Long returnId, Long approvedBy) {
        SaleReturn saleReturn = getReturnById(returnId);
        if (saleReturn.getStatus() != SaleReturn.ReturnStatus.PENDING) {
            throw new RuntimeException("Only pending returns can be approved");
        }

        Sale sale = saleRepository.findById(saleReturn.getSaleId())
                .orElseThrow(() -> new RuntimeException("Original sale not found"));

        List<SaleReturnItem> items = returnItemRepository.findByReturnId(returnId);
        Long companyId = authContextService.getCurrentCompanyId();

        for (SaleReturnItem item : items) {
            // stock restore
            inventoryService.addStock(
                item.getProductId(),
                saleReturn.getWarehouseId(),
                item.getReturnedQuantity(),
                companyId
            );

            // stock movement record
            movementService.recordMovement(
                item.getProductId(),
                saleReturn.getWarehouseId(),
                StockMovement.MovementType.RETURN,
                item.getReturnedQuantity(),
                null, null,
                saleReturn.getReturnId(),
                saleReturn.getReturnNo(),
                companyId,
                approvedBy
            );
        }

        // customer due adjustment
        if (sale.getCustomerId() != null) {
            BigDecimal newDue = sale.getDueAmount().subtract(saleReturn.getTotalReturnAmount());
            if (newDue.compareTo(BigDecimal.ZERO) < 0) newDue = BigDecimal.ZERO;
            sale.setDueAmount(newDue);
            if (newDue.compareTo(BigDecimal.ZERO) == 0) {
                sale.setPaymentStatus(Sale.PaymentStatus.PAID);
            }
            saleRepository.save(sale);
        }

        saleReturn.setStatus(SaleReturn.ReturnStatus.APPROVED);
        saleReturn.setApprovedBy(approvedBy);
        saleReturn.setUpdatedAt(LocalDateTime.now());
        return returnRepository.save(saleReturn);
    }

    @Transactional
    @Auditable(action = "REJECT", entityType = "SaleReturn")
    public void rejectReturn(Long returnId, String reason) {
        SaleReturn saleReturn = getReturnById(returnId);
        if (saleReturn.getStatus() != SaleReturn.ReturnStatus.PENDING) {
            throw new RuntimeException("Only pending returns can be rejected");
        }
        saleReturn.setStatus(SaleReturn.ReturnStatus.REJECTED);
        saleReturn.setNotes(reason);
        saleReturn.setUpdatedAt(LocalDateTime.now());
        returnRepository.save(saleReturn);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "SaleReturn")
    public void deleteReturn(Long returnId) {
        SaleReturn saleReturn = getReturnById(returnId);
        if (saleReturn.getStatus() != SaleReturn.ReturnStatus.PENDING) {
            throw new RuntimeException("Only pending returns can be deleted");
        }
        returnItemRepository.deleteByReturnId(returnId);
        returnRepository.deleteById(returnId);
    }
}
