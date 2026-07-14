package com.trademaster.ims.service;

import com.trademaster.ims.model.*;
import com.trademaster.ims.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductRepository productRepository;
    private final WarehouseRepository warehouseRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;
    private final StockMovementRepository stockMovementRepository;

    public DashboardService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            PurchaseRepository purchaseRepository,
            PurchaseItemRepository purchaseItemRepository,
            InventoryRepository inventoryRepository,
            ProductRepository productRepository,
            WarehouseRepository warehouseRepository,
            CustomerRepository customerRepository,
            SupplierRepository supplierRepository,
            StockMovementRepository stockMovementRepository
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
        this.stockMovementRepository = stockMovementRepository;
    }

    public Map<String, Object> getSummary() {
        LocalDate today = LocalDate.now();
        BigDecimal todaySales = totalSalesBetween(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        BigDecimal yesterdaySales = totalSalesBetween(today.minusDays(1).atStartOfDay(), today.atStartOfDay());
        BigDecimal stockValue = calculateInventoryValue();

        long lowStock = getLowStockAlerts().size();
        long outOrLowStock = getOutOfStock().size();
        long activeWarehouses = warehouseRepository.findAll().stream()
                .filter(w -> w.getStatus() == null || "ACTIVE".equalsIgnoreCase(w.getStatus()) || "1".equals(w.getStatus()))
                .count();

        Map<String, Object> trends = new HashMap<>();
        trends.put("productsTrend", 0);
        trends.put("stockValueTrend", 0);
        trends.put("salesTrend", percentChange(todaySales, yesterdaySales));
        trends.put("pendingPoTrend", 0);
        trends.put("lowStockTrend", lowStock - outOrLowStock);
        trends.put("warehouseTrend", 0);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalProducts", productRepository.count());
        summary.put("totalStockValue", stockValue);
        summary.put("todaysSales", todaySales);
        summary.put("pendingPurchaseOrders", purchaseRepository.countByStatus(Purchase.PurchaseStatus.PENDING));
        summary.put("lowStockItems", lowStock);
        summary.put("activeWarehouses", activeWarehouses);
        summary.put("trends", trends);
        return summary;
    }

    public Map<String, Object> getSalesAnalytics(String period) {
        List<Bucket> buckets = buildBuckets(period);
        List<BigDecimal> sales = new ArrayList<>();
        List<BigDecimal> purchases = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (Bucket bucket : buckets) {
            labels.add(bucket.label());
            sales.add(totalSalesBetween(bucket.start(), bucket.end()));
            purchases.add(totalPurchasesBetween(bucket.start().toLocalDate(), bucket.end().minusNanos(1).toLocalDate()));
        }

        BigDecimal totalSales = sumBigDecimal(sales);
        BigDecimal totalPurchases = sumBigDecimal(purchases);
        BigDecimal previousSales = previousBucketTotalSales(period);
        BigDecimal previousPurchases = previousBucketTotalPurchases(period);

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("sales", sales);
        result.put("purchases", purchases);
        result.put("totalSales", totalSales);
        result.put("totalPurchases", totalPurchases);
        result.put("salesTrend", percentChange(totalSales, previousSales));
        result.put("purchaseTrend", percentChange(totalPurchases, previousPurchases));
        return result;
    }

    public Map<String, Object> getStockMovement(String period) {
        List<Bucket> buckets = buildBuckets(period == null ? "monthly" : period);
        List<Integer> stockIn = new ArrayList<>();
        List<Integer> stockOut = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (Bucket bucket : buckets) {
            labels.add(bucket.label());
            List<StockMovement> movements = stockMovementRepository.findByCreatedAtBetween(bucket.start(), bucket.end());
            int in = 0;
            int out = 0;
            for (StockMovement movement : movements) {
                int qty = Math.abs(nullToZero(movement.getQuantity()));
                if (isStockIn(movement)) {
                    in += qty;
                } else {
                    out += qty;
                }
            }
            stockIn.add(in);
            stockOut.add(out);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("stockIn", stockIn);
        result.put("stockOut", stockOut);
        result.put("totalStockIn", stockIn.stream().mapToInt(Integer::intValue).sum());
        result.put("totalStockOut", stockOut.stream().mapToInt(Integer::intValue).sum());
        return result;
    }

    public List<Map<String, Object>> getWarehouseStockValue() {
        List<Inventory> inventories = inventoryRepository.findAll();
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        Map<Long, Warehouse> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity(), (a, b) -> a));

        Map<Long, BigDecimal> values = new LinkedHashMap<>();
        for (Inventory inventory : inventories) {
            Product product = products.get(inventory.getProductId());
            BigDecimal price = product != null && product.getBuyingPrice() != null ? product.getBuyingPrice() : BigDecimal.ZERO;
            BigDecimal value = price.multiply(BigDecimal.valueOf(availableQuantity(inventory)));
            values.merge(inventory.getWarehouseId(), value, BigDecimal::add);
        }

        BigDecimal total = values.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return values.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(entry -> {
                    Map<String, Object> row = new HashMap<>();
                    Warehouse warehouse = warehouses.get(entry.getKey());
                    row.put("warehouseName", warehouse != null ? warehouse.getName() : "N/A");
                    row.put("stockValue", entry.getValue());
                    row.put("percentage", total.compareTo(BigDecimal.ZERO) > 0
                            ? entry.getValue().multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
                    return row;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getProfitOverview(String period) {
        List<Bucket> buckets = buildBuckets(period == null ? "monthly" : period);
        List<String> labels = new ArrayList<>();
        List<BigDecimal> profits = new ArrayList<>();
        BigDecimal revenue = BigDecimal.ZERO;
        int completedOrderCount = 0;

        for (Bucket bucket : buckets) {
            labels.add(bucket.label());
            List<Sale> sales = saleRepository.findCompletedSalesBetween(bucket.start(), bucket.end());
            revenue = revenue.add(sales.stream().map(s -> nullToZero(s.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add));
            completedOrderCount += sales.size();
            profits.add(calculateProfitForSales(sales));
        }

        BigDecimal totalProfit = sumBigDecimal(profits);
        BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) > 0
                ? totalProfit.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Map<String, Object> result = new HashMap<>();
        result.put("totalProfit", totalProfit);
        result.put("grossProfit", totalProfit);
        result.put("netProfitMargin", margin);
        result.put("averageOrderValue", completedOrderCount > 0 ? revenue.divide(BigDecimal.valueOf(completedOrderCount), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("labels", labels);
        result.put("profitData", profits);
        result.put("profitTrend", profits.size() > 1 ? percentChange(profits.get(profits.size() - 1), profits.get(profits.size() - 2)) : 0);
        return result;
    }

    public List<Map<String, Object>> getTopCustomers(int limit) {
        return saleRepository.findTopCustomers(PageRequest.of(0, Math.max(1, limit))).stream()
                .map(row -> {
                    Map<String, Object> safe = new HashMap<>();
                    safe.put("customerName", row.getOrDefault("customerName", "N/A"));
                    safe.put("customerCode", row.getOrDefault("customerCode", ""));
                    safe.put("photoUrl", row.getOrDefault("photoUrl", ""));
                    safe.put("totalSales", nullToZero((BigDecimal) row.get("totalSpent")));
                    safe.put("trend", 0);
                    return safe;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getLowStockAlerts() {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        Map<Long, Warehouse> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity(), (a, b) -> a));

        return inventoryRepository.findAll().stream()
                .filter(inv -> {
                    Product product = products.get(inv.getProductId());
                    int reorder = reorderLevel(product);
                    return reorder > 0 && availableQuantity(inv) <= reorder;
                })
                .sorted(Comparator.comparingInt(this::availableQuantity))
                .map(inv -> {
                    Product product = products.get(inv.getProductId());
                    Warehouse warehouse = warehouses.get(inv.getWarehouseId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("productName", product != null ? product.getProductName() : "N/A");
                    row.put("imageUrl", product != null ? product.getImageUrl() : "");
                    row.put("warehouseName", warehouse != null ? warehouse.getName() : "N/A");
                    row.put("currentQuantity", availableQuantity(inv));
                    row.put("reorderLevel", reorderLevel(product));
                    return row;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getOutOfStock() {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return inventoryRepository.findAll().stream()
                .filter(inv -> {
                    Product product = products.get(inv.getProductId());
                    return availableQuantity(inv) <= 0 || (reorderLevel(product) > 0 && availableQuantity(inv) <= reorderLevel(product));
                })
                .sorted(Comparator.comparingInt(this::availableQuantity))
                .map(inv -> {
                    Product product = products.get(inv.getProductId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("productName", product != null ? product.getProductName() : "N/A");
                    row.put("imageUrl", product != null ? product.getImageUrl() : "");
                    row.put("status", availableQuantity(inv) <= 0 ? "Out of Stock" : "Reorder Required");
                    return row;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getRecentStockMovements(int limit) {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        Map<Long, Warehouse> warehouses = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(Warehouse::getId, Function.identity(), (a, b) -> a));

        return stockMovementRepository.findAll(PageRequest.of(0, Math.max(1, limit), Sort.by("createdAt").descending()))
                .getContent()
                .stream()
                .map(movement -> {
                    Product product = products.get(movement.getProductId());
                    Warehouse warehouse = warehouses.get(movement.getWarehouseId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", movement.getCreatedAt());
                    row.put("productName", product != null ? product.getProductName() : "N/A");
                    row.put("imageUrl", product != null ? product.getImageUrl() : "");
                    row.put("warehouseName", warehouse != null ? warehouse.getName() : "N/A");
                    row.put("type", movement.getMovementType() != null ? movement.getMovementType().name() : "N/A");
                    row.put("quantity", nullToZero(movement.getQuantity()));
                    row.put("status", "Completed");
                    return row;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopSellingProducts(int limit) {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        Map<Long, List<SaleItem>> byProduct = saleItemRepository.findAll().stream()
                .collect(Collectors.groupingBy(SaleItem::getProductId));

        return byProduct.entrySet().stream()
                .map(entry -> {
                    Product product = products.get(entry.getKey());
                    int units = entry.getValue().stream().mapToInt(item -> nullToZero(item.getQuantity())).sum();
                    BigDecimal revenue = entry.getValue().stream()
                            .map(item -> nullToZero(item.getTotalPrice()))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    Map<String, Object> row = new HashMap<>();
                    row.put("productName", product != null ? product.getProductName() : "N/A");
                    row.put("imageUrl", product != null ? product.getImageUrl() : "");
                    row.put("unitsSold", units);
                    row.put("revenue", revenue);
                    row.put("trend", 0);
                    return row;
                })
                .sorted((a, b) -> Integer.compare((Integer) b.get("unitsSold"), (Integer) a.get("unitsSold")))
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getRecentPurchaseOrders(int limit) {
        Map<Long, Supplier> suppliers = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getSupplierId, Function.identity(), (a, b) -> a));

        return purchaseRepository.findAll(PageRequest.of(0, Math.max(1, limit), Sort.by("purchaseDate").descending().and(Sort.by("createdAt").descending())))
                .getContent()
                .stream()
                .map(purchase -> {
                    Supplier supplier = suppliers.get(purchase.getSupplierId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("poNo", purchase.getPurchaseOrderNo());
                    row.put("supplierName", supplier != null ? supplier.getSupplierName() : "N/A");
                    row.put("date", purchase.getPurchaseDate());
                    row.put("amount", nullToZero(purchase.getTotalAmount()));
                    row.put("status", purchase.getStatus() != null ? purchase.getStatus().name() : "N/A");
                    return row;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStats() { return getSummary(); }
    public Map<String, Object> getSalesTrend() { return getSalesAnalytics("daily"); }
    public Map<String, Object> getProfitTrend() { return getProfitOverview("daily"); }
    public Map<String, Object> getTopProducts() { return Map.of("topProducts", getTopSellingProducts(5)); }
    public List<Sale> getRecentSales(int limit) {
        return saleRepository.findAll(PageRequest.of(0, Math.max(1, limit), Sort.by("saleDate").descending())).getContent();
    }
    public List<Purchase> getRecentPurchases(int limit) {
        return purchaseRepository.findAll(PageRequest.of(0, Math.max(1, limit), Sort.by("purchaseDate").descending())).getContent();
    }

    private BigDecimal calculateInventoryValue() {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        return inventoryRepository.findAll().stream()
                .map(inv -> {
                    Product product = products.get(inv.getProductId());
                    BigDecimal buyingPrice = product != null && product.getBuyingPrice() != null ? product.getBuyingPrice() : BigDecimal.ZERO;
                    return buyingPrice.multiply(BigDecimal.valueOf(availableQuantity(inv)));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateProfitForSales(List<Sale> sales) {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));
        BigDecimal profit = BigDecimal.ZERO;
        for (Sale sale : sales) {
            for (SaleItem item : saleItemRepository.findBySaleId(sale.getSaleId())) {
                BigDecimal revenue = nullToZero(item.getTotalPrice());
                Product product = products.get(item.getProductId());
                BigDecimal cost = product != null && product.getBuyingPrice() != null
                        ? product.getBuyingPrice().multiply(BigDecimal.valueOf(nullToZero(item.getQuantity())))
                        : BigDecimal.ZERO;
                profit = profit.add(revenue.subtract(cost));
            }
        }
        return profit;
    }

    private BigDecimal totalSalesBetween(LocalDateTime start, LocalDateTime end) {
        return nullToZero(saleRepository.getTotalSalesRevenueBetween(start, end));
    }

    private BigDecimal totalPurchasesBetween(LocalDate start, LocalDate end) {
        return purchaseRepository.findAllByPurchaseDateBetween(start, end).stream()
                .map(p -> nullToZero(p.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal previousBucketTotalSales(String period) {
        LocalDateTime now = LocalDateTime.now();
        if ("hourly".equalsIgnoreCase(period)) return totalSalesBetween(now.minusHours(2), now.minusHours(1));
        if ("weekly".equalsIgnoreCase(period)) return totalSalesBetween(now.minusWeeks(2), now.minusWeeks(1));
        if ("monthly".equalsIgnoreCase(period)) return totalSalesBetween(now.minusMonths(2), now.minusMonths(1));
        return totalSalesBetween(LocalDate.now().minusDays(1).atStartOfDay(), LocalDate.now().atStartOfDay());
    }

    private BigDecimal previousBucketTotalPurchases(String period) {
        LocalDate today = LocalDate.now();
        if ("weekly".equalsIgnoreCase(period)) return totalPurchasesBetween(today.minusWeeks(2), today.minusWeeks(1));
        if ("monthly".equalsIgnoreCase(period)) return totalPurchasesBetween(today.minusMonths(2), today.minusMonths(1));
        if ("hourly".equalsIgnoreCase(period)) return BigDecimal.ZERO;
        return totalPurchasesBetween(today.minusDays(1), today.minusDays(1));
    }

    private List<Bucket> buildBuckets(String period) {
        String normalized = period == null ? "monthly" : period.toLowerCase(Locale.ROOT);
        List<Bucket> buckets = new ArrayList<>();
        LocalDate today = LocalDate.now();
        DateTimeFormatter monthFormat = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

        if ("hourly".equals(normalized)) {
            LocalDateTime hour = LocalDateTime.now().minusHours(5).withMinute(0).withSecond(0).withNano(0);
            for (int i = 0; i < 6; i++) {
                buckets.add(new Bucket(hour.plusHours(i).format(DateTimeFormatter.ofPattern("ha", Locale.ENGLISH)),
                        hour.plusHours(i), hour.plusHours(i + 1)));
            }
            return buckets;
        }
        if ("weekly".equals(normalized)) {
            for (int i = 5; i >= 0; i--) {
                LocalDate start = today.minusWeeks(i).minusDays(today.minusWeeks(i).getDayOfWeek().getValue() - 1L);
                buckets.add(new Bucket("W" + start.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)),
                        start.atStartOfDay(), start.plusDays(7).atStartOfDay()));
            }
            return buckets;
        }
        if ("daily".equals(normalized)) {
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                buckets.add(new Bucket(date.format(DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)),
                        date.atStartOfDay(), date.plusDays(1).atStartOfDay()));
            }
            return buckets;
        }
        for (int i = 5; i >= 0; i--) {
            LocalDate date = today.minusMonths(i).withDayOfMonth(1);
            buckets.add(new Bucket(date.format(monthFormat), date.atStartOfDay(), date.plusMonths(1).atStartOfDay()));
        }
        return buckets;
    }

    private boolean isStockIn(StockMovement movement) {
        return movement.getMovementType() == StockMovement.MovementType.PURCHASE
                || movement.getMovementType() == StockMovement.MovementType.RETURN
                || (movement.getQuantity() != null && movement.getQuantity() > 0
                && movement.getMovementType() != StockMovement.MovementType.SALE);
    }

    private int availableQuantity(Inventory inventory) {
        if (inventory == null) return 0;
        if (inventory.getAvailableQuantity() != null) return inventory.getAvailableQuantity();
        return nullToZero(inventory.getQuantity()) - nullToZero(inventory.getReservedQuantity());
    }

    private int reorderLevel(Product product) {
        if (product == null) return 0;
        if (product.getReorderLevel() != null) return product.getReorderLevel();
        return product.getMinStockLevel() != null ? product.getMinStockLevel() : 0;
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return current != null && current.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumBigDecimal(List<BigDecimal> values) {
        return values.stream().map(this::nullToZero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nullToZero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private int nullToZero(Integer value) { return value == null ? 0 : value; }

    private record Bucket(String label, LocalDateTime start, LocalDateTime end) {}
}
