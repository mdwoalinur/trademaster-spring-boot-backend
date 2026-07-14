package com.trademaster.ims.service;

import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.model.Purchase.PurchaseStatus;
import com.trademaster.ims.model.PurchaseItem;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.model.StockMovement;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.repository.PaymentAllocationRepository;
import com.trademaster.ims.repository.PurchaseRepository;
import com.trademaster.ims.repository.PurchaseItemRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PurchaseService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PurchaseItemRepository itemRepository;

    @Autowired
    private InventoryService inventoryService;
    
    @Autowired
    private StockMovementService movementService;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private InvoiceEmailService invoiceEmailService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    // ==================== Query Methods ====================
    
    public Page<Purchase> getPurchases(Pageable pageable, String status, String search) {
        if (search != null && !search.isEmpty()) {
            return purchaseRepository.searchByOrderNo(search, pageable);
        } else if (status != null && !status.isEmpty()) {
            try {
                return purchaseRepository.findByStatus(PurchaseStatus.valueOf(status), pageable);
            } catch (IllegalArgumentException e) {
                return purchaseRepository.findAll(pageable);
            }
        } else {
            return purchaseRepository.findAll(pageable);
        }
    }
    
    public Page<Purchase> getPurchasesBySupplier(Long supplierId, Pageable pageable) {
        return purchaseRepository.findBySupplierId(supplierId, pageable);
    }
    
    public Page<Purchase> getPurchasesByWarehouse(Long warehouseId, Pageable pageable) {
        return purchaseRepository.findByWarehouseId(warehouseId, pageable);
    }

    public Purchase getPurchaseById(Long id) {
        return purchaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Purchase not found with id: " + id));
    }
    
    public Purchase getPurchaseByOrderNo(String orderNo) {
        return purchaseRepository.findByPurchaseOrderNo(orderNo)
                .orElseThrow(() -> new RuntimeException("Purchase not found with order no: " + orderNo));
    }

    // ==================== CRUD Methods ====================

    @Transactional
    @Auditable(action = "CREATE", entityType = "Purchase")
    public Purchase createPurchase(Purchase purchase) {
        // Validate required fields
        if (purchase.getSupplierId() == null) {
            throw new RuntimeException("Supplier is required");
        }
        if (purchase.getItems() == null || purchase.getItems().isEmpty()) {
            throw new RuntimeException("At least one item is required");
        }
        
        purchase.setUserId(authContextService.getCurrentUserId());
        calculateTotals(purchase);
        for (PurchaseItem item : purchase.getItems()) {
            item.setPurchase(purchase);
        }
        Purchase savedPurchase = purchaseRepository.save(purchase);
        queuePurchaseOrderAfterCommit(savedPurchase);
        return savedPurchase;
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Purchase")
    public Purchase updatePurchase(Long id, Purchase purchaseData) {
        Purchase existing = getPurchaseById(id);
        
        // Only allow update if status is PENDING
        if (existing.getStatus() != PurchaseStatus.PENDING) {
            throw new RuntimeException("Only pending purchases can be updated");
        }
        
        existing.setSupplierId(purchaseData.getSupplierId());
        existing.setWarehouseId(purchaseData.getWarehouseId());
        existing.setPurchaseDate(purchaseData.getPurchaseDate());
        existing.setExpectedDelivery(purchaseData.getExpectedDelivery());
        existing.setPaymentTerms(purchaseData.getPaymentTerms());
        existing.setNotes(purchaseData.getNotes());

        // Clear and replace items
        existing.getItems().clear();
        for (PurchaseItem item : purchaseData.getItems()) {
            item.setPurchase(existing);
            existing.getItems().add(item);
        }
        calculateTotals(existing);
        return purchaseRepository.save(existing);
    }

    // ==================== Business Logic Methods ====================

    @Transactional
    @Auditable(action = "RECEIVE", entityType = "Purchase")
    public Purchase markAsReceived(Long id, LocalDate deliveryDate, String notes) {
        Purchase purchase = getPurchaseById(id);
        
        // Validate status
        if (purchase.getStatus() == PurchaseStatus.RECEIVED) {
            throw new RuntimeException("Purchase is already marked as received");
        }
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new RuntimeException("Cannot receive a cancelled purchase");
        }
        
        purchase.setActualDelivery(deliveryDate);
        if (notes != null && !notes.isEmpty()) {
            purchase.setNotes(notes);
        }
        purchase.setStatus(PurchaseStatus.RECEIVED);
        
        // Update inventory and record stock movement for each item
        Long companyId = authContextService.getCurrentCompanyId();
        Long userId = authContextService.getCurrentUserId();
        
        for (PurchaseItem item : purchase.getItems()) {
            // Get current stock before adding
            Integer currentStockBefore = inventoryService.getCurrentStock(
                item.getProductId(), 
                purchase.getWarehouseId()
            );
            
            // Add stock to inventory
            inventoryService.addStock(
                item.getProductId(), 
                purchase.getWarehouseId(), 
                item.getQuantity(),
                companyId
            );
            
            // Get new stock after adding
            Integer currentStockAfter = inventoryService.getCurrentStock(
                item.getProductId(), 
                purchase.getWarehouseId()
            );
            
            // Record stock movement
            movementService.recordMovement(
                item.getProductId(),
                purchase.getWarehouseId(),
                StockMovement.MovementType.PURCHASE,
                item.getQuantity(),
                currentStockBefore,
                currentStockAfter,
                purchase.getPurchaseId(),
                purchase.getPurchaseOrderNo(),
                companyId,
                userId
            );
        }
        
        Purchase saved = purchaseRepository.save(purchase);
        notificationService.createPurchaseReceivedNotification(companyId, userId, saved.getPurchaseId(), saved.getPurchaseOrderNo());
        return saved;
    }

    @Transactional
    @Auditable(action = "CANCEL", entityType = "Purchase")
    public void cancelPurchase(Long id) {
        Purchase purchase = getPurchaseById(id);
        
        // Validate status
        if (purchase.getStatus() == PurchaseStatus.RECEIVED) {
            throw new RuntimeException("Cannot cancel a received purchase");
        }
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new RuntimeException("Purchase is already cancelled");
        }
        
        purchase.setStatus(PurchaseStatus.CANCELLED);
        purchaseRepository.save(purchase);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Purchase")
    public void deletePurchase(Long id) {
        Purchase purchase = getPurchaseById(id);
        
        // Prevent deletion of received purchases
        if (purchase.getStatus() == PurchaseStatus.RECEIVED) {
            throw new RuntimeException("Cannot delete a received purchase. Cancel it first if needed.");
        }
        
        // Delete associated items first
        itemRepository.deleteByPurchase_PurchaseId(id);
        purchaseRepository.delete(purchase);
    }

    // ==================== Helper Methods ====================

    private void calculateTotals(Purchase purchase) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO; // ✅ ডিসকাউন্টও এভাবেই ক্যালকুলেট করতে হবে
        
        for (PurchaseItem item : purchase.getItems()) {
            // ✅ item.getSubtotal() থেকে আসল টাকা পাচ্ছি
            BigDecimal itemSubtotal = item.getSubtotal();
            subtotal = subtotal.add(itemSubtotal != null ? itemSubtotal : BigDecimal.ZERO);
            
            // ✅ item.getTaxAmount() থেকে ট্যাক্সের আসল টাকা পাচ্ছি
            totalTax = totalTax.add(item.getTaxAmount() != null ? item.getTaxAmount() : BigDecimal.ZERO);
            
            // ✅ item.getDiscountAmount() থেকে ডিসকাউন্টের আসল টাকা বাদ দিচ্ছি
            totalDiscount = totalDiscount.add(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO);
        }
        
        purchase.setSubtotal(subtotal);
        purchase.setTaxAmount(totalTax);
        
        // ✅ মোট বিল = (সাবটোটাল + ট্যাক্স) - ডিসকাউন্ট
        BigDecimal total = subtotal.add(totalTax).subtract(totalDiscount);
        purchase.setTotalAmount(total);
        BigDecimal paid = purchase.getPaidAmount() != null ? purchase.getPaidAmount() : BigDecimal.ZERO;
        purchase.setDueAmount(total.subtract(paid).max(BigDecimal.ZERO));
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            purchase.setPaymentStatus(Purchase.PaymentStatus.UNPAID);
        } else if (paid.compareTo(total) >= 0) {
            purchase.setPaymentStatus(Purchase.PaymentStatus.PAID);
        } else {
            purchase.setPaymentStatus(Purchase.PaymentStatus.PARTIAL);
        }
    }

    @Transactional
    @Auditable(action = "REQUEST_PAYMENT", entityType = "Purchase")
    public Payment requestPayment(Long purchaseId, Map<String, Object> payload) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Purchase not found with id: " + purchaseId));
        if (purchase.getStatus() != PurchaseStatus.RECEIVED) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Only received purchases can have payment requests");
        }
        BigDecimal posted = paymentAllocationRepository.getPostedAmountForReference(PaymentAllocation.ReferenceType.PURCHASE, purchaseId);
        BigDecimal due = purchase.getTotalAmount().subtract(posted);
        if (due.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Purchase is already fully paid");
        }
        BigDecimal requested = payload != null && payload.get("requestedAmount") != null
                ? new BigDecimal(payload.get("requestedAmount").toString())
                : due;
        if (requested.compareTo(BigDecimal.ZERO) <= 0 || requested.compareTo(due) > 0) {
            throw new ApiResponseException(HttpStatus.UNPROCESSABLE_ENTITY, "Requested amount must be greater than zero and not exceed due amount");
        }

        Payment payment = new Payment();
        payment.setDirection(Payment.PaymentDirection.PAY);
        payment.setPaymentType(Payment.PaymentType.PURCHASE);
        payment.setPartyType(Payment.PartyType.SUPPLIER);
        payment.setPartyId(purchase.getSupplierId());
        payment.setReferenceId(purchase.getPurchaseId());
        payment.setReferenceNo(purchase.getPurchaseOrderNo());
        payment.setPaymentDate(java.time.LocalDateTime.now());
        payment.setAmount(requested);
        payment.setRequestedAmount(requested);
        payment.setPaymentMethod(parsePaymentMethod(payload != null ? payload.get("paymentMethod") : null));
        payment.setNotes(payload != null && payload.get("notes") != null
                ? payload.get("notes").toString()
                : "Purchase payment request awaiting admin approval");
        payment.setWarehouseId(purchase.getWarehouseId());
        if (payload != null && payload.get("accountId") != null) {
            payment.setAccountId(Long.valueOf(payload.get("accountId").toString()));
        }
        return paymentService.createSubmittedPaymentRequest(payment);
    }

    public Map<String, Object> getPaymentSummary(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Purchase not found with id: " + purchaseId));
        return paymentService.getReferencePaymentSummary(PaymentAllocation.ReferenceType.PURCHASE, purchaseId, purchase.getTotalAmount());
    }

    public List<PaymentAllocation> getPurchasePayments(Long purchaseId) {
        return paymentAllocationRepository.findByReferenceTypeAndReferenceId(PaymentAllocation.ReferenceType.PURCHASE, purchaseId);
    }

    private Payment.PaymentMethod parsePaymentMethod(Object methodValue) {
        if (methodValue == null || methodValue.toString().isBlank()) {
            return Payment.PaymentMethod.CASH;
        }
        try {
            return Payment.PaymentMethod.valueOf(methodValue.toString());
        } catch (IllegalArgumentException ignored) {
            return Payment.PaymentMethod.CASH;
        }
    }
    
    /**
     * Check if purchase can be modified
     */
    public boolean isModifiable(Long id) {
        Purchase purchase = getPurchaseById(id);
        return purchase.getStatus() == PurchaseStatus.PENDING;
    }
    
    /**
     * Get total purchase amount for dashboard
     */
    public BigDecimal getTotalPurchaseAmount(LocalDate startDate, LocalDate endDate) {
        // Implementation can be added later
        return BigDecimal.ZERO;
    }
    
    @Transactional
    public List<Purchase> createBulkPurchases(List<Purchase> purchases) {
        List<Purchase> createdPurchases = new ArrayList<>();
        for (Purchase purchase : purchases) {
            createdPurchases.add(createPurchase(purchase));
        }
        return createdPurchases;
    }

    private void queuePurchaseOrderAfterCommit(Purchase purchase) {
        if (purchase != null && purchase.getPurchaseId() != null && purchase.getStatus() == PurchaseStatus.PENDING) {
            runAfterCommit(() -> invoiceEmailService.sendPurchaseOrderAsync(purchase.getPurchaseId(), false));
        }
    }

    private void runAfterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }
}
