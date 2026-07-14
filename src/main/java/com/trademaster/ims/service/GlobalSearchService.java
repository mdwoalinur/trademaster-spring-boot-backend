package com.trademaster.ims.service;

import com.trademaster.ims.model.*;
import com.trademaster.ims.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GlobalSearchService {

    private static final int PAGE_LIMIT = 250;
    private static final int MODULE_LIMIT = 6;
    private static final int DEFAULT_TOTAL_LIMIT = 50;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final ProductVariationRepository productVariationRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SaleRepository saleRepository;
    private final SaleReturnRepository saleReturnRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockTransferRepository stockTransferRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final LowStockAlertRepository lowStockAlertRepository;
    private final WastageCategoryRepository wastageCategoryRepository;
    private final WastageRecordRepository wastageRecordRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public GlobalSearchService(ProductRepository productRepository,
                               CategoryRepository categoryRepository,
                               UnitRepository unitRepository,
                               ProductVariationRepository productVariationRepository,
                               CustomerRepository customerRepository,
                               SupplierRepository supplierRepository,
                               WarehouseRepository warehouseRepository,
                               PurchaseRepository purchaseRepository,
                               PurchaseReturnRepository purchaseReturnRepository,
                               SaleRepository saleRepository,
                               SaleReturnRepository saleReturnRepository,
                               InventoryRepository inventoryRepository,
                               StockMovementRepository stockMovementRepository,
                               StockTransferRepository stockTransferRepository,
                               StockAdjustmentRepository stockAdjustmentRepository,
                               LowStockAlertRepository lowStockAlertRepository,
                               WastageCategoryRepository wastageCategoryRepository,
                               WastageRecordRepository wastageRecordRepository,
                               ExpenseRepository expenseRepository,
                               ExpenseCategoryRepository expenseCategoryRepository,
                               PaymentRepository paymentRepository,
                               UserRepository userRepository,
                               RoleRepository roleRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.unitRepository = unitRepository;
        this.productVariationRepository = productVariationRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.warehouseRepository = warehouseRepository;
        this.purchaseRepository = purchaseRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.saleRepository = saleRepository;
        this.saleReturnRepository = saleReturnRepository;
        this.inventoryRepository = inventoryRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockTransferRepository = stockTransferRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.lowStockAlertRepository = lowStockAlertRepository;
        this.wastageCategoryRepository = wastageCategoryRepository;
        this.wastageRecordRepository = wastageRecordRepository;
        this.expenseRepository = expenseRepository;
        this.expenseCategoryRepository = expenseCategoryRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public List<Map<String, Object>> searchResults(String query, Integer limit) {
        String term = query == null ? "" : query.trim();
        int totalLimit = limit == null || limit <= 0 ? DEFAULT_TOTAL_LIMIT : Math.min(limit, 100);
        if (term.length() < 2) return List.of();

        List<Map<String, Object>> results = new ArrayList<>();
        add(results, searchProducts(term));
        add(results, searchCategories(term));
        add(results, searchUnits(term));
        add(results, searchProductVariations(term));
        add(results, searchCustomers(term));
        add(results, searchSuppliers(term));
        add(results, searchWarehouses(term));
        add(results, searchPurchases(term));
        add(results, searchPurchaseReturns(term));
        add(results, searchSales(term));
        add(results, searchSaleReturns(term));
        add(results, searchInventory(term));
        add(results, searchStockMovements(term));
        add(results, searchStockTransfers(term));
        add(results, searchStockAdjustments(term));
        add(results, searchLowStockAlerts(term));
        add(results, searchWastageCategories(term));
        add(results, searchWastageRecords(term));
        add(results, searchExpenses(term));
        add(results, searchExpenseCategories(term));
        add(results, searchPayments(term));
        add(results, searchUsers(term));
        add(results, searchRoles(term));
        add(results, searchReports(term));

        return results.stream().limit(totalLimit).collect(Collectors.toList());
    }

    public Map<String, Object> search(String keyword) {
        List<Map<String, Object>> items = searchResults(keyword, DEFAULT_TOTAL_LIMIT);
        Map<String, Object> grouped = new LinkedHashMap<>();
        grouped.put("products", filterLegacy(items, "PRODUCT", "CATEGORY", "UNIT", "PRODUCT_VARIATION"));
        grouped.put("customers", filterLegacy(items, "CUSTOMER"));
        grouped.put("suppliers", filterLegacy(items, "SUPPLIER"));
        grouped.put("sales", filterLegacy(items, "SALE", "SALE_RETURN"));
        grouped.put("purchases", filterLegacy(items, "PURCHASE", "PURCHASE_RETURN"));
        grouped.put("warehouses", filterLegacy(items, "WAREHOUSE"));
        grouped.put("inventory", filterLegacy(items, "INVENTORY", "STOCK_MOVEMENT", "STOCK_TRANSFER", "STOCK_ADJUSTMENT", "LOW_STOCK_ALERT", "WASTAGE_CATEGORY", "WASTAGE_RECORD"));
        grouped.put("finance", filterLegacy(items, "EXPENSE", "EXPENSE_CATEGORY", "PAYMENT"));
        grouped.put("system", filterLegacy(items, "USER", "ROLE", "REPORT"));
        return grouped;
    }

    private List<Map<String, Object>> searchProducts(String term) {
        return productRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(p -> active(p.getStatus()) && matches(term, p.getProductName(), p.getProductCode(), p.getSku(), p.getDescription()))
                .limit(MODULE_LIMIT)
                .map(p -> item("PRODUCT", "Products", p.getId(), safe(p.getProductName(), "Product #" + p.getId()),
                        join("Code: " + safe(p.getProductCode(), "N/A"), "SKU: " + safe(p.getSku(), "N/A")),
                        "/products/edit/" + p.getId(), "bi-box-seam", p.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchCategories(String term) {
        return categoryRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(c -> active(c.getStatus()) && matches(term, c.getCategoryName(), c.getDescription()))
                .limit(MODULE_LIMIT)
                .map(c -> item("CATEGORY", "Products", c.getCategoryId(), safe(c.getCategoryName(), "Category #" + c.getCategoryId()),
                        safe(c.getDescription(), "Product category"), "/products/categories/edit/" + c.getCategoryId(), "bi-tags", c.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchUnits(String term) {
        return unitRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(u -> active(u.getStatus()) && matches(term, u.getUnitName(), u.getUnitCode(), u.getUnitType()))
                .limit(MODULE_LIMIT)
                .map(u -> item("UNIT", "Products", u.getUnitId(), safe(u.getUnitName(), "Unit #" + u.getUnitId()),
                        join("Code: " + safe(u.getUnitCode(), "N/A"), String.valueOf(u.getUnitType())), "/units/edit/" + u.getUnitId(), "bi-rulers", u.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchProductVariations(String term) {
        return productVariationRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(v -> active(v.getStatus()) && matches(term, v.getVariationName(), v.getSku(), v.getDisplayName()))
                .limit(MODULE_LIMIT)
                .map(v -> item("PRODUCT_VARIATION", "Products", v.getVariationId(), safe(firstText(v.getDisplayName(), v.getVariationName()), "Variation #" + v.getVariationId()),
                        join("SKU: " + safe(v.getSku(), "N/A"), "Product ID: " + v.getProductId()), "/products/variations/edit/" + v.getVariationId(), "bi-grid", v.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchCustomers(String term) {
        return customerRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(c -> active(c.getStatus()) && matches(term, c.getCustomerName(), c.getCustomerCode(), c.getEmail(), c.getMobile(), c.getPhone()))
                .limit(MODULE_LIMIT)
                .map(c -> {
                    Map<String, Object> row = item("CUSTOMER", "Customers", c.getCustomerId(), safe(c.getCustomerName(), "Customer #" + c.getCustomerId()),
                            join(safe(c.getCustomerCode(), "N/A"), safe(c.getEmail(), "No email"), safe(firstText(c.getMobile(), c.getPhone()), "No phone")),
                            "/customers/edit/" + c.getCustomerId(), "bi-people", c.getStatus());
                    row.put("photoUrl", c.getPhotoUrl());
                    row.put("customerCode", c.getCustomerCode());
                    row.put("email", c.getEmail());
                    row.put("phone", firstText(c.getMobile(), c.getPhone()));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchSuppliers(String term) {
        return supplierRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(s -> active(s.getStatus()) && matches(term, s.getSupplierName(), s.getSupplierCode(), s.getEmail(), s.getPhone(), s.getContactPerson()))
                .limit(MODULE_LIMIT)
                .map(s -> {
                    Map<String, Object> row = item("SUPPLIER", "Suppliers", s.getSupplierId(), safe(s.getSupplierName(), "Supplier #" + s.getSupplierId()),
                            join(safe(s.getSupplierCode(), "N/A"), safe(s.getEmail(), "No email"), safe(s.getPhone(), "No phone")),
                            "/suppliers/edit/" + s.getSupplierId(), "bi-building", s.getStatus());
                    row.put("photoUrl", s.getPhotoUrl());
                    row.put("supplierCode", s.getSupplierCode());
                    row.put("email", s.getEmail());
                    row.put("phone", s.getPhone());
                    row.put("contactPerson", s.getContactPerson());
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchWarehouses(String term) {
        return warehouseRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(w -> matches(term, w.getName(), w.getWarehouseCode(), w.getLocation(), w.getManagerName(), w.getStatus()))
                .limit(MODULE_LIMIT)
                .map(w -> item("WAREHOUSE", "Warehouses", w.getId(), safe(w.getName(), "Warehouse #" + w.getId()),
                        join("Code: " + safe(w.getWarehouseCode(), "N/A"), safe(w.getLocation(), "No location")),
                        "/warehouses/edit/" + w.getId(), "bi-houses", w.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchPurchases(String term) {
        Map<Long, Supplier> suppliers = supplierMap();
        return purchaseRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(p -> matches(term, p.getPurchaseOrderNo(), p.getStatus(), supplierName(suppliers, p.getSupplierId()), p.getNotes()))
                .limit(MODULE_LIMIT)
                .map(p -> item("PURCHASE", "Purchases", p.getPurchaseId(), safe(p.getPurchaseOrderNo(), "Purchase #" + p.getPurchaseId()),
                        join(supplierName(suppliers, p.getSupplierId()), String.valueOf(p.getStatus())), "/purchases/view/" + p.getPurchaseId(), "bi-truck", p.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchPurchaseReturns(String term) {
        Map<Long, Supplier> suppliers = supplierMap();
        return purchaseReturnRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(r -> matches(term, r.getReturnNo(), r.getReason(), r.getStatus(), supplierName(suppliers, r.getSupplierId())))
                .limit(MODULE_LIMIT)
                .map(r -> item("PURCHASE_RETURN", "Purchases", r.getId(), safe(r.getReturnNo(), "Purchase Return #" + r.getId()),
                        join(supplierName(suppliers, r.getSupplierId()), String.valueOf(r.getStatus())), "/purchase-returns/view/" + r.getId(), "bi-arrow-counterclockwise", r.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchSales(String term) {
        Map<Long, Customer> customers = customerMap();
        return saleRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(s -> matches(term, s.getInvoiceNo(), s.getStatus(), s.getPaymentStatus(), customerName(customers, s.getCustomerId()), s.getNotes()))
                .limit(MODULE_LIMIT)
                .map(s -> item("SALE", "Sales", s.getSaleId(), safe(s.getInvoiceNo(), "Sale #" + s.getSaleId()),
                        join(customerName(customers, s.getCustomerId()), String.valueOf(s.getStatus()), String.valueOf(s.getPaymentStatus())),
                        "/sales/edit/" + s.getSaleId(), "bi-receipt", s.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchSaleReturns(String term) {
        Map<Long, Customer> customers = customerMap();
        return saleReturnRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(r -> matches(term, r.getReturnNo(), r.getInvoiceNo(), r.getReason(), r.getStatus(), customerName(customers, r.getCustomerId())))
                .limit(MODULE_LIMIT)
                .map(r -> item("SALE_RETURN", "Sales", r.getReturnId(), safe(r.getReturnNo(), "Sale Return #" + r.getReturnId()),
                        join(safe(r.getInvoiceNo(), "No invoice"), customerName(customers, r.getCustomerId()), String.valueOf(r.getStatus())),
                        "/sales-returns/view/" + r.getReturnId(), "bi-arrow-return-left", r.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchInventory(String term) {
        Map<Long, Product> products = productMap();
        Map<Long, Warehouse> warehouses = warehouseMap();
        return inventoryRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(i -> matches(term, productName(products, i.getProductId()), warehouseName(warehouses, i.getWarehouseId()), i.getQuantity(), i.getAvailableQuantity()))
                .limit(MODULE_LIMIT)
                .map(i -> item("INVENTORY", "Inventory", i.getInventoryId(), productName(products, i.getProductId()),
                        join(warehouseName(warehouses, i.getWarehouseId()), "Available: " + i.getAvailableQuantity()), "/inventory", "bi-archive", "STOCK"))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchStockMovements(String term) {
        return stockMovementRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(m -> matches(term, m.getReferenceNo(), m.getMovementType(), m.getNotes(), m.getProductId(), m.getWarehouseId()))
                .limit(MODULE_LIMIT)
                .map(m -> item("STOCK_MOVEMENT", "Inventory", m.getMovementId(), safe(m.getReferenceNo(), "Movement #" + m.getMovementId()),
                        join(String.valueOf(m.getMovementType()), "Qty: " + m.getQuantity()), "/inventory/movements", "bi-arrow-left-right", m.getMovementType()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchStockTransfers(String term) {
        return stockTransferRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(t -> matches(term, t.getTransferNo(), t.getStatus(), t.getReason(), t.getNotes()))
                .limit(MODULE_LIMIT)
                .map(t -> item("STOCK_TRANSFER", "Inventory", t.getTransferId(), safe(t.getTransferNo(), "Transfer #" + t.getTransferId()),
                        join("From: " + t.getFromWarehouseId(), "To: " + t.getToWarehouseId(), String.valueOf(t.getStatus())),
                        "/stock-transfers/view/" + t.getTransferId(), "bi-arrow-left-right", t.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchStockAdjustments(String term) {
        return stockAdjustmentRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(a -> matches(term, a.getReason(), a.getStatus(), a.getNotes(), a.getProductId(), a.getWarehouseId()))
                .limit(MODULE_LIMIT)
                .map(a -> item("STOCK_ADJUSTMENT", "Inventory", a.getAdjustmentId(), "Adjustment #" + a.getAdjustmentId(),
                        join("Product ID: " + a.getProductId(), "Difference: " + a.getDifference(), String.valueOf(a.getStatus())),
                        "/inventory/adjustments", "bi-sliders2", a.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchLowStockAlerts(String term) {
        Map<Long, Product> products = productMap();
        return lowStockAlertRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(a -> matches(term, productName(products, a.getProductId()), a.getProductId(), a.getWarehouseId(), a.getCurrentQuantity()))
                .limit(MODULE_LIMIT)
                .map(a -> item("LOW_STOCK_ALERT", "Inventory", a.getAlertId(), productName(products, a.getProductId()),
                        join("Current: " + a.getCurrentQuantity(), "Reorder: " + a.getReorderLevel()), "/inventory/low-stock", "bi-bell", "LOW_STOCK"))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchWastageCategories(String term) {
        return wastageCategoryRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(c -> active(c.getStatus()) && matches(term, c.getCategoryName(), c.getCategoryCode(), c.getDescription()))
                .limit(MODULE_LIMIT)
                .map(c -> item("WASTAGE_CATEGORY", "Wastage", c.getCategoryId(), safe(c.getCategoryName(), "Wastage Category #" + c.getCategoryId()),
                        join("Code: " + safe(c.getCategoryCode(), "N/A"), safe(c.getDescription(), "")), "/inventory/wastage/categories/edit/" + c.getCategoryId(), "bi-trash3", c.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchWastageRecords(String term) {
        return wastageRecordRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(w -> matches(term, w.getWastageType(), w.getReason(), w.getBatchNo(), w.getStatus(), w.getNotes()))
                .limit(MODULE_LIMIT)
                .map(w -> item("WASTAGE_RECORD", "Wastage", w.getWastageId(), "Wastage Record #" + w.getWastageId(),
                        join(String.valueOf(w.getWastageType()), "Qty: " + w.getQuantity(), String.valueOf(w.getStatus())),
                        "/inventory/wastage/records/edit/" + w.getWastageId(), "bi-list", w.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchExpenses(String term) {
        return expenseRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(e -> matches(term, e.getExpenseNo(), e.getReferenceNo(), e.getStatus(), e.getPaymentStatus(), e.getNotes()))
                .limit(MODULE_LIMIT)
                .map(e -> item("EXPENSE", "Finance", e.getExpenseId(), safe(e.getExpenseNo(), "Expense #" + e.getExpenseId()),
                        join(String.valueOf(e.getStatus()), "Total: " + e.getGrandTotal()), "/expenses/view/" + e.getExpenseId(), "bi-receipt-cutoff", e.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchExpenseCategories(String term) {
        return expenseCategoryRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(c -> active(c.getStatus()) && matches(term, c.getCategoryName(), c.getCategoryCode(), c.getDescription()))
                .limit(MODULE_LIMIT)
                .map(c -> item("EXPENSE_CATEGORY", "Finance", c.getExpCategoryId(), safe(c.getCategoryName(), "Expense Category #" + c.getExpCategoryId()),
                        join("Code: " + safe(c.getCategoryCode(), "N/A"), safe(c.getDescription(), "")), "/expenses/categories/edit/" + c.getExpCategoryId(), "bi-tags", c.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchPayments(String term) {
        return paymentRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(p -> matches(term, p.getReferenceNo(), p.getPaymentType(), p.getPaymentStatus(), p.getPaymentMethod(), p.getNotes()))
                .limit(MODULE_LIMIT)
                .map(p -> item("PAYMENT", "Finance", p.getPaymentId(), safe(p.getReferenceNo(), "Payment #" + p.getPaymentId()),
                        join(String.valueOf(p.getPaymentType()), String.valueOf(p.getPaymentStatus()), "Amount: " + p.getAmount()),
                        "/payments/edit/" + p.getPaymentId(), "bi-cash-stack", p.getPaymentStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchUsers(String term) {
        return userRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(u -> active(u.getIsActive()) && matches(term, u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(), u.getDepartment(), u.getDesignation()))
                .limit(MODULE_LIMIT)
                .map(u -> item("USER", "System", u.getUserId(), safe(firstText(u.getFullName(), u.getUsername()), "User #" + u.getUserId()),
                        join(safe(u.getEmail(), "No email"), safe(u.getDesignation(), "")), "/users/edit/" + u.getUserId(), "bi-person-badge", u.getIsActive() ? "ACTIVE" : "INACTIVE"))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchRoles(String term) {
        return roleRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .filter(r -> active(r.getStatus()) && matches(term, r.getRoleName(), r.getDescription()))
                .limit(MODULE_LIMIT)
                .map(r -> item("ROLE", "System", r.getRoleId(), safe(r.getRoleName(), "Role #" + r.getRoleId()),
                        safe(r.getDescription(), "System role"), "/roles/edit/" + r.getRoleId(), "bi-shield-lock", r.getStatus()))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> searchReports(String term) {
        return List.of(
                item("REPORT", "Reports", 1L, "Sales Report", "Sales analytics and invoice reporting", "/reports/sales", "bi-graph-up", "REPORT"),
                item("REPORT", "Reports", 2L, "Purchase Report", "Purchase order reporting", "/reports/purchases", "bi-cart-check", "REPORT"),
                item("REPORT", "Reports", 3L, "Inventory Report", "Stock and inventory reporting", "/reports/inventory", "bi-pie-chart", "REPORT"),
                item("REPORT", "Reports", 4L, "Profit & Loss", "Revenue, expense and profit reporting", "/reports/profit-loss", "bi-cash-stack", "REPORT"),
                item("REPORT", "Reports", 5L, "Tax Report", "GST / VAT compliance reporting", "/reports/tax", "bi-file-earmark-text", "REPORT"),
                item("REPORT", "Reports", 6L, "Notifications History", "System notification history", "/notifications", "bi-bell", "REPORT")
        ).stream().filter(r -> matches(term, r.get("title"), r.get("subtitle"))).collect(Collectors.toList());
    }

    private void add(List<Map<String, Object>> target, List<Map<String, Object>> source) {
        target.addAll(source);
    }

    private List<Map<String, Object>> filterLegacy(List<Map<String, Object>> items, String... types) {
        Set<String> allowed = new HashSet<>(Arrays.asList(types));
        return items.stream().filter(item -> allowed.contains(String.valueOf(item.get("type")))).collect(Collectors.toList());
    }

    private Map<String, Object> item(String type, String module, Long id, String title, String subtitle, String route, String icon, Object status) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", type);
        item.put("module", module);
        item.put("title", title);
        item.put("subtitle", subtitle == null ? "" : subtitle);
        item.put("route", route);
        item.put("icon", icon);
        item.put("status", status == null ? "" : String.valueOf(status));
        item.put("id", id);
        return item;
    }

    private boolean matches(String term, Object... values) {
        String needle = term.toLowerCase(Locale.ROOT);
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(value -> String.valueOf(value).toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(needle));
    }

    private boolean active(Boolean status) {
        return status == null || status;
    }

    private boolean active(Object status) {
        if (status == null) return true;
        String value = String.valueOf(status);
        return !"INACTIVE".equalsIgnoreCase(value) && !"DELETED".equalsIgnoreCase(value);
    }

    private Map<Long, Product> productMap() {
        return productRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }

    private Map<Long, Warehouse> warehouseMap() {
        return warehouseRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w, (a, b) -> a));
    }

    private Map<Long, Supplier> supplierMap() {
        return supplierRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .collect(Collectors.toMap(Supplier::getSupplierId, s -> s, (a, b) -> a));
    }

    private Map<Long, Customer> customerMap() {
        return customerRepository.findAll(PageRequest.of(0, PAGE_LIMIT)).getContent().stream()
                .collect(Collectors.toMap(Customer::getCustomerId, c -> c, (a, b) -> a));
    }

    private String productName(Map<Long, Product> products, Long id) {
        Product product = products.get(id);
        return product == null ? "Product #" + id : safe(product.getProductName(), "Product #" + id);
    }

    private String warehouseName(Map<Long, Warehouse> warehouses, Long id) {
        Warehouse warehouse = warehouses.get(id);
        return warehouse == null ? "Warehouse #" + id : safe(warehouse.getName(), "Warehouse #" + id);
    }

    private String supplierName(Map<Long, Supplier> suppliers, Long id) {
        Supplier supplier = suppliers.get(id);
        return supplier == null ? "Supplier #" + id : safe(supplier.getSupplierName(), "Supplier #" + id);
    }

    private String customerName(Map<Long, Customer> customers, Long id) {
        Customer customer = customers.get(id);
        return customer == null ? "Customer #" + id : safe(customer.getCustomerName(), "Customer #" + id);
    }

    private String join(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank() && !"null".equalsIgnoreCase(part))
                .collect(Collectors.joining(" | "));
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstText(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
