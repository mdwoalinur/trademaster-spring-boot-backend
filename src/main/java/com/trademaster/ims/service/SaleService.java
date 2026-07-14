package com.trademaster.ims.service;

import com.trademaster.ims.model.Sale;
import com.trademaster.ims.model.SaleItem;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.SaleItemRepository;
import com.trademaster.ims.repository.SaleRepository;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SaleService {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleItemRepository saleItemRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private AuthContextService authContextService;

    @Autowired
    private InvoiceEmailService invoiceEmailService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PaymentService paymentService;

    public List<Sale> getAllSales() {
        return saleRepository.findAll();
    }

    public Sale getSaleById(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found with id: " + id));
    }

    public List<Sale> getSalesByCustomer(Long customerId) {
        return saleRepository.findByCustomerId(customerId);
    }

    public List<Sale> getSalesByWarehouse(Long warehouseId) {
        return saleRepository.findByWarehouseId(warehouseId);
    }

    public List<Sale> getSalesByStatus(Sale.SaleStatus status) {
        return saleRepository.findByStatus(status);
    }

    public List<Sale> getSalesByPaymentStatus(Sale.PaymentStatus paymentStatus) {
        return saleRepository.findByPaymentStatus(paymentStatus);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Sale")
    public Sale createSale(Sale sale) {
        sale.setSaleId(null);
        sale.setUserId(authContextService.getCurrentUserId());

        BigDecimal requestedPayment = sale.getPaidAmount() != null ? sale.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal total = sale.getTotalAmount() != null ? sale.getTotalAmount() : BigDecimal.ZERO;
        sale.setPaidAmount(BigDecimal.ZERO);
        sale.setDueAmount(total);
        sale.setPaymentStatus(Sale.PaymentStatus.UNPAID);

        Sale savedSale = saleRepository.save(sale);
        if (requestedPayment.compareTo(BigDecimal.ZERO) > 0) {
            createSalePaymentRequest(savedSale, requestedPayment.min(total), "Sale payment request awaiting admin approval");
        }
        notificationService.createSaleNotification(authContextService.getCurrentCompanyId(), authContextService.getCurrentUserId(), savedSale.getSaleId(), savedSale.getInvoiceNo());
        if (savedSale.getDueAmount() != null && savedSale.getDueAmount().compareTo(BigDecimal.ZERO) > 0) {
            notificationService.createPaymentDueNotification(authContextService.getCurrentCompanyId(), authContextService.getCurrentUserId(), savedSale.getSaleId(), savedSale.getInvoiceNo(), savedSale.getDueAmount());
        }
        queueSaleInvoiceAfterCommit(savedSale);
        return savedSale;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @Auditable(action = "CREATE", entityType = "Sale")
    public Sale createPosSale(Map<String, Object> request) {

        List<Map<String, Object>> items =
                (List<Map<String, Object>>) request.get("items");

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        Long customerId = request.get("customerId") != null
                ? Long.valueOf(request.get("customerId").toString())
                : null;
        if (customerId == null) {
            throw new IllegalArgumentException("Customer is required. Please create/select Walk-in Customer.");
        }

        Long warehouseId = request.get("warehouseId") != null
                ? Long.valueOf(request.get("warehouseId").toString())
                : null;

        Long userId = authContextService.getCurrentUserId();

        if (warehouseId == null) {
            throw new IllegalArgumentException("Warehouse is required");
        }

        BigDecimal subtotal = new BigDecimal(request.get("subtotal").toString());
        BigDecimal discountAmount = new BigDecimal(request.get("discountAmount").toString());
        BigDecimal taxAmount = new BigDecimal(request.get("taxAmount").toString());
        BigDecimal totalAmount = new BigDecimal(request.get("totalAmount").toString());
        BigDecimal paidAmount = new BigDecimal(request.get("paidAmount").toString());

        String notes = request.get("notes") != null
                ? request.get("notes").toString()
                : "";

        Sale sale = new Sale();
        sale.setInvoiceNo("INV-" + System.currentTimeMillis());
        sale.setCustomerId(customerId);
        sale.setWarehouseId(warehouseId);
        sale.setUserId(userId);
        sale.setSaleDate(LocalDateTime.now());
        sale.setSubtotal(subtotal);
        sale.setDiscountAmount(discountAmount);
        sale.setTaxAmount(taxAmount);
        sale.setTotalAmount(totalAmount);
        sale.setPaidAmount(BigDecimal.ZERO);
        sale.setDueAmount(totalAmount);
        sale.setNotes(notes);
        sale.setStatus(Sale.SaleStatus.COMPLETED);
        sale.setPaymentStatus(Sale.PaymentStatus.UNPAID);

        Sale savedSale = saleRepository.save(sale);

        for (Map<String, Object> itemMap : items) {

            Long productId = Long.valueOf(itemMap.get("productId").toString());
            Integer quantity = Integer.valueOf(itemMap.get("quantity").toString());

            BigDecimal unitPrice = new BigDecimal(itemMap.get("unitPrice").toString());

            BigDecimal discountPercent = itemMap.get("discountPercent") != null
                    ? new BigDecimal(itemMap.get("discountPercent").toString())
                    : BigDecimal.ZERO;

            BigDecimal discountAmt = itemMap.get("discountAmount") != null
                    ? new BigDecimal(itemMap.get("discountAmount").toString())
                    : BigDecimal.ZERO;

            BigDecimal taxRate = itemMap.get("taxRate") != null
                    ? new BigDecimal(itemMap.get("taxRate").toString())
                    : BigDecimal.ZERO;

            BigDecimal totalPrice = new BigDecimal(itemMap.get("totalPrice").toString());

            inventoryService.removeStock(productId, warehouseId, quantity);
            notificationService.createLowStockNotification(authContextService.getCurrentCompanyId(), userId, productId, warehouseId, inventoryService.getCurrentStock(productId, warehouseId));

            SaleItem saleItem = new SaleItem();
            saleItem.setSaleId(savedSale.getSaleId());
            saleItem.setProductId(productId);
            saleItem.setQuantity(quantity);
            saleItem.setUnitPrice(unitPrice);
            saleItem.setDiscountPercent(discountPercent);
            saleItem.setDiscountAmount(discountAmt);
            saleItem.setTaxRate(taxRate);
            saleItem.setTotalPrice(totalPrice);

            saleItemRepository.save(saleItem);
        }

        queueSaleInvoiceAfterCommit(savedSale);
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) {
            paymentService.createPosPaymentRequest(savedSale, paidAmount.min(totalAmount),
                    request.get("paymentMethod") != null ? request.get("paymentMethod").toString() : "CASH",
                    "POS payment request awaiting admin approval");
        }
        notificationService.createSaleNotification(authContextService.getCurrentCompanyId(), userId, savedSale.getSaleId(), savedSale.getInvoiceNo());
        if (savedSale.getDueAmount() != null && savedSale.getDueAmount().compareTo(BigDecimal.ZERO) > 0) {
            notificationService.createPaymentDueNotification(authContextService.getCurrentCompanyId(), userId, savedSale.getSaleId(), savedSale.getInvoiceNo(), savedSale.getDueAmount());
        }
        return savedSale;
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Sale")
    public Sale updateSale(Long id, Sale saleDetails) {
        Sale existing = getSaleById(id);

        existing.setInvoiceNo(saleDetails.getInvoiceNo());
        existing.setCustomerId(saleDetails.getCustomerId());
        existing.setWarehouseId(saleDetails.getWarehouseId());
        existing.setSaleDate(saleDetails.getSaleDate());
        existing.setSubtotal(saleDetails.getSubtotal());
        existing.setDiscountAmount(saleDetails.getDiscountAmount());
        existing.setTaxAmount(saleDetails.getTaxAmount());
        existing.setTotalAmount(saleDetails.getTotalAmount());
        existing.setPaidAmount(saleDetails.getPaidAmount());

        if (saleDetails.getTotalAmount() != null && saleDetails.getPaidAmount() != null) {
            existing.setDueAmount(saleDetails.getTotalAmount().subtract(saleDetails.getPaidAmount()));
        }

        existing.setPaymentStatus(saleDetails.getPaymentStatus());
        existing.setNotes(saleDetails.getNotes());
        existing.setStatus(saleDetails.getStatus());

        return saleRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Sale")
    public void deleteSale(Long id) {
        saleRepository.deleteById(id);
    }

    @Transactional
    public List<Sale> createBulkSales(List<Sale> sales) {
        List<Sale> createdSales = new ArrayList<>();
        for (Sale sale : sales) {
            createdSales.add(createSale(sale));
        }
        return createdSales;
    }

    private void queueSaleInvoiceAfterCommit(Sale sale) {
        if (sale != null && sale.getSaleId() != null && sale.getStatus() == Sale.SaleStatus.COMPLETED) {
            runAfterCommit(() -> invoiceEmailService.sendSaleInvoiceAsync(sale.getSaleId(), false));
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

    private void createSalePaymentRequest(Sale sale, BigDecimal requestedAmount, String notes) {
        Payment payment = new Payment();
        payment.setDirection(Payment.PaymentDirection.RECEIVE);
        payment.setPaymentType(Payment.PaymentType.SALE);
        payment.setPartyType(Payment.PartyType.CUSTOMER);
        payment.setPartyId(sale.getCustomerId());
        payment.setReferenceId(sale.getSaleId());
        payment.setReferenceNo(sale.getInvoiceNo());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setAmount(requestedAmount);
        payment.setRequestedAmount(requestedAmount);
        payment.setPaymentMethod(Payment.PaymentMethod.CASH);
        payment.setWarehouseId(sale.getWarehouseId());
        payment.setNotes(notes);
        paymentService.createSubmittedPaymentRequest(payment);
    }
}
