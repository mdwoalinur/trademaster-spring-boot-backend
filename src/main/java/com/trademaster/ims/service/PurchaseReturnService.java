package com.trademaster.ims.service;

import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.model.*;
import com.trademaster.ims.repository.*;
import com.trademaster.ims.security.AuthContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseReturnService {

    private final PurchaseReturnRepository returnRepository;
    private final PurchaseReturnItemRepository itemRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final InventoryService inventoryService;
    private final StockMovementService stockMovementService;
    private final AuthContextService authContextService;
    private final NotificationService notificationService;

    public PurchaseReturnService(PurchaseReturnRepository returnRepository,
                                 PurchaseReturnItemRepository itemRepository,
                                 PurchaseRepository purchaseRepository,
                                 PurchaseItemRepository purchaseItemRepository,
                                 ProductRepository productRepository,
                                 SupplierRepository supplierRepository,
                                 WarehouseRepository warehouseRepository,
                                 InventoryService inventoryService,
                                 StockMovementService stockMovementService,
                                 AuthContextService authContextService,
                                 NotificationService notificationService) {
        this.returnRepository = returnRepository;
        this.itemRepository = itemRepository;
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.inventoryService = inventoryService;
        this.stockMovementService = stockMovementService;
        this.authContextService = authContextService;
        this.notificationService = notificationService;
    }

    public List<Map<String, Object>> getAll(String status, String search, LocalDate startDate, LocalDate endDate) {
        PurchaseReturn.PurchaseReturnStatus parsedStatus = parseStatus(status);
        String keyword = blankToNull(search);
        List<PurchaseReturn> returns = returnRepository.searchReturns(parsedStatus, null, startDate, endDate);
        return returns.stream()
                .map(this::toSummary)
                .filter(row -> matchesSearch(row, keyword))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getById(Long id) {
        return toDetail(getReturn(id));
    }

    public List<Map<String, Object>> getByPurchase(Long purchaseId) {
        return returnRepository.findByOriginalPurchaseId(purchaseId).stream().map(this::toSummary).collect(Collectors.toList());
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "PurchaseReturn")
    public PurchaseReturn create(PurchaseReturn request) {
        Purchase purchase = getPurchase(request.getOriginalPurchaseId());
        request.setId(null);
        request.setReturnNo(generateReturnNo());
        request.setStatus(PurchaseReturn.PurchaseReturnStatus.DRAFT);
        request.setSupplierId(purchase.getSupplierId());
        request.setWarehouseId(purchase.getWarehouseId());
        request.setReturnDate(request.getReturnDate() == null ? LocalDate.now() : request.getReturnDate());
        request.setCreatedBy(authContextService.getCurrentUserId());
        if (request.getItems() == null) {
            request.setItems(new ArrayList<>());
        }
        replaceAndValidateItems(request, purchase);
        calculateTotals(request);
        return returnRepository.save(request);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "PurchaseReturn")
    public PurchaseReturn update(Long id, PurchaseReturn request) {
        PurchaseReturn existing = getReturn(id);
        ensureDraft(existing);
        Purchase purchase = getPurchase(existing.getOriginalPurchaseId());
        existing.setReturnDate(request.getReturnDate() == null ? existing.getReturnDate() : request.getReturnDate());
        existing.setReason(request.getReason());
        existing.setNotes(request.getNotes());
        List<PurchaseReturnItem> requestedItems = request.getItems() == null ? new ArrayList<>() : request.getItems();
        List<PurchaseReturnItem> validatedItems = buildValidatedItems(existing, purchase, requestedItems);
        existing.getItems().clear();
        existing.getItems().addAll(validatedItems);
        calculateTotals(existing);
        return returnRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "PurchaseReturn")
    public void delete(Long id) {
        PurchaseReturn purchaseReturn = getReturn(id);
        ensureDraft(purchaseReturn);
        returnRepository.delete(purchaseReturn);
    }

    @Transactional
    @Auditable(action = "CONFIRM", entityType = "PurchaseReturn")
    public PurchaseReturn confirm(Long id) {
        PurchaseReturn purchaseReturn = getReturn(id);
        ensureDraft(purchaseReturn);
        Purchase purchase = getPurchase(purchaseReturn.getOriginalPurchaseId());
        replaceAndValidateItems(purchaseReturn, purchase);
        calculateTotals(purchaseReturn);

        Long companyId = authContextService.getCurrentCompanyId();
        Long userId = authContextService.getCurrentUserId();

        for (PurchaseReturnItem item : purchaseReturn.getItems()) {
            int currentStock = inventoryService.getCurrentStock(item.getProductId(), purchaseReturn.getWarehouseId());
            if (currentStock < item.getQuantity()) {
                throw new RuntimeException("Insufficient stock for product #" + item.getProductId() + ". Available: " + currentStock);
            }
        }

        for (PurchaseReturnItem item : purchaseReturn.getItems()) {
            int previousStock = inventoryService.getCurrentStock(item.getProductId(), purchaseReturn.getWarehouseId());
            inventoryService.removeStock(item.getProductId(), purchaseReturn.getWarehouseId(), item.getQuantity());
            int newStock = inventoryService.getCurrentStock(item.getProductId(), purchaseReturn.getWarehouseId());
            notificationService.createLowStockNotification(companyId, userId, item.getProductId(), purchaseReturn.getWarehouseId(), newStock);
            StockMovement movement = new StockMovement();
            movement.setCompanyId(companyId);
            movement.setProductId(item.getProductId());
            movement.setWarehouseId(purchaseReturn.getWarehouseId());
            movement.setMovementType(StockMovement.MovementType.PURCHASE_RETURN);
            movement.setReferenceId(purchaseReturn.getId());
            movement.setReferenceNo(purchaseReturn.getReturnNo());
            movement.setQuantity(item.getQuantity());
            movement.setPreviousStock(previousStock);
            movement.setNewStock(newStock);
            movement.setNotes(item.getReason() != null ? item.getReason() : purchaseReturn.getReason());
            movement.setCreatedBy(userId);
            movement.setCreatedAt(LocalDateTime.now());
            stockMovementService.createMovement(movement);
        }

        purchaseReturn.setStatus(PurchaseReturn.PurchaseReturnStatus.CONFIRMED);
        PurchaseReturn saved = returnRepository.save(purchaseReturn);
        notificationService.createPurchaseReturnConfirmedNotification(companyId, userId, saved.getId(), saved.getReturnNo());
        return saved;
    }

    public List<Map<String, Object>> getReturnableItems(Long purchaseId) {
        Purchase purchase = getPurchase(purchaseId);
        if (purchase.getStatus() != Purchase.PurchaseStatus.RECEIVED) {
            throw new RuntimeException("Only received purchases can be returned");
        }
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return purchaseItemRepository.findByPurchase_PurchaseId(purchaseId).stream().map(item -> {
            int returned = zero(itemRepository.getConfirmedReturnedQuantity(item.getPurchaseItemId()));
            int qty = zero(item.getQuantity());
            Product product = products.get(item.getProductId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("purchaseItemId", item.getPurchaseItemId());
            row.put("productId", item.getProductId());
            row.put("productName", product != null ? product.getProductName() : "Product #" + item.getProductId());
            row.put("sku", product != null ? product.getSku() : "");
            row.put("imageUrl", product != null ? product.getImageUrl() : "");
            row.put("purchasedQuantity", qty);
            row.put("alreadyReturnedQuantity", returned);
            row.put("returnableQuantity", Math.max(qty - returned, 0));
            row.put("unitPrice", zero(item.getUnitPrice()));
            row.put("taxRate", zero(item.getTax()));
            row.put("discountRate", zero(item.getDiscount()));
            return row;
        }).collect(Collectors.toList());
    }

    private void replaceAndValidateItems(PurchaseReturn purchaseReturn, Purchase purchase) {
        if (purchase == null) throw new RuntimeException("Purchase is required");
        if (!Objects.equals(purchaseReturn.getSupplierId(), purchase.getSupplierId()) ||
                !Objects.equals(purchaseReturn.getWarehouseId(), purchase.getWarehouseId())) {
            throw new RuntimeException("Supplier and warehouse must match original purchase");
        }
        if (purchaseReturn.getReason() == null || purchaseReturn.getReason().isBlank()) {
            throw new RuntimeException("Return reason is required");
        }
        List<PurchaseReturnItem> validItems = new ArrayList<>();
        Map<Long, PurchaseItem> purchaseItems = purchaseItemRepository.findByPurchase_PurchaseId(purchase.getPurchaseId()).stream()
                .collect(Collectors.toMap(PurchaseItem::getPurchaseItemId, Function.identity()));
        for (PurchaseReturnItem item : purchaseReturn.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) continue;
            PurchaseItem purchaseItem = purchaseItems.get(item.getPurchaseItemId());
            if (purchaseItem == null) throw new RuntimeException("Invalid purchase item selected");
            int returnable = zero(purchaseItem.getQuantity()) - zero(itemRepository.getConfirmedReturnedQuantity(purchaseItem.getPurchaseItemId()));
            if (item.getQuantity() > returnable) {
                throw new RuntimeException("Return quantity exceeds returnable quantity for product #" + purchaseItem.getProductId());
            }
            item.setPurchaseReturn(purchaseReturn);
            item.setProductId(purchaseItem.getProductId());
            item.setUnitPrice(zero(item.getUnitPrice()).compareTo(BigDecimal.ZERO) > 0 ? item.getUnitPrice() : zero(purchaseItem.getUnitPrice()));
            item.setTaxRate(zero(item.getTaxRate()));
            item.setDiscountAmount(zero(item.getDiscountAmount()));
            validItems.add(item);
        }
        if (validItems.isEmpty()) throw new RuntimeException("At least one return item is required");
        purchaseReturn.getItems().clear();
        purchaseReturn.getItems().addAll(validItems);
    }

    private List<PurchaseReturnItem> buildValidatedItems(PurchaseReturn purchaseReturn,
                                                         Purchase purchase,
                                                         List<PurchaseReturnItem> requestedItems) {
        if (purchase == null) throw new RuntimeException("Purchase is required");
        if (!Objects.equals(purchaseReturn.getSupplierId(), purchase.getSupplierId()) ||
                !Objects.equals(purchaseReturn.getWarehouseId(), purchase.getWarehouseId())) {
            throw new RuntimeException("Supplier and warehouse must match original purchase");
        }
        if (purchaseReturn.getReason() == null || purchaseReturn.getReason().isBlank()) {
            throw new RuntimeException("Return reason is required");
        }

        List<PurchaseReturnItem> validItems = new ArrayList<>();
        Map<Long, PurchaseItem> purchaseItems = purchaseItemRepository.findByPurchase_PurchaseId(purchase.getPurchaseId()).stream()
                .collect(Collectors.toMap(PurchaseItem::getPurchaseItemId, Function.identity()));

        for (PurchaseReturnItem requestedItem : requestedItems) {
            if (requestedItem.getQuantity() == null || requestedItem.getQuantity() <= 0) continue;
            PurchaseItem purchaseItem = purchaseItems.get(requestedItem.getPurchaseItemId());
            if (purchaseItem == null) throw new RuntimeException("Invalid purchase item selected");

            int returnable = zero(purchaseItem.getQuantity()) - zero(itemRepository.getConfirmedReturnedQuantity(purchaseItem.getPurchaseItemId()));
            if (requestedItem.getQuantity() > returnable) {
                throw new RuntimeException("Return quantity exceeds returnable quantity for product #" + purchaseItem.getProductId());
            }

            PurchaseReturnItem item = new PurchaseReturnItem();
            item.setPurchaseReturn(purchaseReturn);
            item.setProductId(purchaseItem.getProductId());
            item.setPurchaseItemId(purchaseItem.getPurchaseItemId());
            item.setQuantity(requestedItem.getQuantity());
            item.setUnitPrice(zero(requestedItem.getUnitPrice()).compareTo(BigDecimal.ZERO) > 0
                    ? requestedItem.getUnitPrice()
                    : zero(purchaseItem.getUnitPrice()));
            item.setTaxRate(zero(requestedItem.getTaxRate()));
            item.setDiscountAmount(zero(requestedItem.getDiscountAmount()));
            item.setReason(requestedItem.getReason());
            validItems.add(item);
        }

        if (validItems.isEmpty()) throw new RuntimeException("At least one return item is required");
        return validItems;
    }

    private void calculateTotals(PurchaseReturn purchaseReturn) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal discount = BigDecimal.ZERO;
        for (PurchaseReturnItem item : purchaseReturn.getItems()) {
            BigDecimal lineSubtotal = zero(item.getUnitPrice()).multiply(BigDecimal.valueOf(zero(item.getQuantity())));
            BigDecimal lineTax = lineSubtotal.multiply(zero(item.getTaxRate())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal lineTotal = lineSubtotal.add(lineTax).subtract(zero(item.getDiscountAmount()));
            item.setTaxAmount(lineTax);
            item.setTotalAmount(lineTotal);
            subtotal = subtotal.add(lineSubtotal);
            tax = tax.add(lineTax);
            discount = discount.add(zero(item.getDiscountAmount()));
        }
        purchaseReturn.setSubtotal(subtotal);
        purchaseReturn.setTaxAmount(tax);
        purchaseReturn.setDiscountAmount(discount);
        purchaseReturn.setTotalAmount(subtotal.add(tax).subtract(discount));
    }

    private Map<String, Object> toSummary(PurchaseReturn pr) {
        Map<String, Object> row = baseMap(pr);
        row.put("items", mapItems(pr.getItems()));
        return row;
    }

    private Map<String, Object> toDetail(PurchaseReturn pr) {
        Map<String, Object> row = baseMap(pr);
        row.put("items", mapItems(pr.getItems()));
        row.put("stockMovements", stockMovementService.getMovementsByType(StockMovement.MovementType.PURCHASE_RETURN).stream()
                .filter(m -> Objects.equals(m.getReferenceId(), pr.getId())).collect(Collectors.toList()));
        return row;
    }

    private Map<String, Object> baseMap(PurchaseReturn pr) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", pr.getId());
        row.put("returnNo", pr.getReturnNo());
        row.put("originalPurchaseId", pr.getOriginalPurchaseId());
        row.put("originalPurchaseNo", purchaseRepository.findById(pr.getOriginalPurchaseId()).map(Purchase::getPurchaseOrderNo).orElse("N/A"));
        row.put("supplierId", pr.getSupplierId());
        row.put("supplierName", supplierRepository.findById(pr.getSupplierId()).map(Supplier::getSupplierName).orElse("N/A"));
        row.put("warehouseId", pr.getWarehouseId());
        row.put("warehouseName", warehouseRepository.findById(pr.getWarehouseId()).map(Warehouse::getName).orElse("N/A"));
        row.put("returnDate", pr.getReturnDate());
        row.put("reason", pr.getReason());
        row.put("subtotal", pr.getSubtotal());
        row.put("taxAmount", pr.getTaxAmount());
        row.put("discountAmount", pr.getDiscountAmount());
        row.put("totalAmount", pr.getTotalAmount());
        row.put("status", pr.getStatus());
        row.put("notes", pr.getNotes());
        row.put("createdAt", pr.getCreatedAt());
        row.put("updatedAt", pr.getUpdatedAt());
        return row;
    }

    private List<Map<String, Object>> mapItems(List<PurchaseReturnItem> items) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return items.stream().map(item -> {
            Product product = products.get(item.getProductId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("purchaseReturnId", item.getPurchaseReturnId());
            row.put("purchaseItemId", item.getPurchaseItemId());
            row.put("productId", item.getProductId());
            row.put("productName", product != null ? product.getProductName() : "Product #" + item.getProductId());
            row.put("sku", product != null ? product.getSku() : "");
            row.put("imageUrl", product != null ? product.getImageUrl() : "");
            row.put("quantity", item.getQuantity());
            row.put("unitPrice", item.getUnitPrice());
            row.put("taxRate", item.getTaxRate());
            row.put("taxAmount", item.getTaxAmount());
            row.put("discountAmount", item.getDiscountAmount());
            row.put("totalAmount", item.getTotalAmount());
            row.put("reason", item.getReason());
            return row;
        }).collect(Collectors.toList());
    }

    private PurchaseReturn getReturn(Long id) {
        return returnRepository.findById(id).orElseThrow(() -> new RuntimeException("Purchase return not found"));
    }

    private Purchase getPurchase(Long id) {
        if (id == null) throw new RuntimeException("Original purchase is required");
        return purchaseRepository.findById(id).orElseThrow(() -> new RuntimeException("Purchase not found"));
    }

    private void ensureDraft(PurchaseReturn purchaseReturn) {
        if (purchaseReturn.getStatus() != PurchaseReturn.PurchaseReturnStatus.DRAFT) {
            throw new RuntimeException("Only draft purchase returns can be modified");
        }
    }

    private PurchaseReturn.PurchaseReturnStatus parseStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return PurchaseReturn.PurchaseReturnStatus.valueOf(status);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private boolean matchesSearch(Map<String, Object> row, String keyword) {
        if (keyword == null) return true;
        String normalized = keyword.toLowerCase(Locale.ROOT);
        return String.valueOf(row.getOrDefault("returnNo", "")).toLowerCase(Locale.ROOT).contains(normalized)
                || String.valueOf(row.getOrDefault("supplierName", "")).toLowerCase(Locale.ROOT).contains(normalized)
                || String.valueOf(row.getOrDefault("originalPurchaseNo", "")).toLowerCase(Locale.ROOT).contains(normalized);
    }

    private int zero(Integer value) { return value == null ? 0 : value; }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }

    private String generateReturnNo() {
        return "PR-" + System.currentTimeMillis();
    }
}
