package com.trademaster.ims.service;

import com.trademaster.ims.model.*;
import com.trademaster.ims.repository.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProfitLossService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final ExpenseCategoryRepository expenseCategoryRepository;

    public ProfitLossService(
            SaleRepository saleRepository,
            SaleItemRepository saleItemRepository,
            ProductRepository productRepository,
            PurchaseRepository purchaseRepository,
            ExpenseRepository expenseRepository,
            ExpenseItemRepository expenseItemRepository,
            ExpenseCategoryRepository expenseCategoryRepository
    ) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.productRepository = productRepository;
        this.purchaseRepository = purchaseRepository;
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.expenseCategoryRepository = expenseCategoryRepository;
    }

    public BigDecimal[] calculateProfitLoss(LocalDate startDate, LocalDate endDate) {
        Map<String, Object> report = generateProfitLossReport(startDate, endDate);
        return new BigDecimal[]{
                (BigDecimal) report.get("revenue"),
                (BigDecimal) report.get("expenses")
        };
    }

    public Map<String, Object> generateProfitLossReport(LocalDate startDate, LocalDate endDate) {
        LocalDate safeStart = startDate != null ? startDate : LocalDate.now().withDayOfMonth(1);
        LocalDate safeEnd = endDate != null ? endDate : LocalDate.now();
        if (safeEnd.isBefore(safeStart)) {
            LocalDate tmp = safeStart;
            safeStart = safeEnd;
            safeEnd = tmp;
        }

        LocalDateTime startDateTime = safeStart.atStartOfDay();
        LocalDateTime endDateTime = safeEnd.atTime(LocalTime.MAX);

        List<Sale> completedSales = saleRepository.findCompletedSalesBetween(startDateTime, endDateTime);
        List<Purchase> purchases = purchaseRepository.findAllByPurchaseDateBetween(safeStart, safeEnd);
        List<Expense> approvedExpenses = expenseRepository.findApprovedBetween(safeStart, safeEnd);
        Map<Long, Product> productMap = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (a, b) -> a));

        BigDecimal totalRevenue = completedSales.stream()
                .map(sale -> zero(sale.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costOfGoodsSold = calculateCogs(completedSales, productMap);
        BigDecimal totalExpenses = approvedExpenses.stream()
                .map(expense -> zero(expense.getGrandTotal()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal grossProfit = totalRevenue.subtract(costOfGoodsSold);
        BigDecimal netProfit = grossProfit.subtract(totalExpenses);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("revenue", totalRevenue);
        report.put("expenses", totalExpenses);
        report.put("netProfit", netProfit);
        report.put("totalRevenue", totalRevenue);
        report.put("costOfGoodsSold", costOfGoodsSold);
        report.put("totalPurchaseCost", costOfGoodsSold);
        report.put("grossProfit", grossProfit);
        report.put("totalExpenses", totalExpenses);
        report.put("grossProfitMargin", margin(grossProfit, totalRevenue));
        report.put("netProfitMargin", margin(netProfit, totalRevenue));
        report.put("salesCount", completedSales.size());
        report.put("purchaseCount", purchases.size());
        report.put("expenseCount", approvedExpenses.size());
        report.put("revenueBreakdown", revenueBreakdown(completedSales, totalRevenue));
        report.put("purchaseBreakdown", purchaseBreakdown(costOfGoodsSold, completedSales.size()));
        report.put("expenseBreakdown", expenseBreakdown(approvedExpenses, totalExpenses));
        report.put("profitTrend", profitTrend(safeStart, safeEnd, productMap));
        report.put("insights", insights(totalRevenue, totalExpenses, netProfit));
        return report;
    }

    private BigDecimal calculateCogs(List<Sale> sales, Map<Long, Product> productMap) {
        BigDecimal totalCost = BigDecimal.ZERO;
        for (Sale sale : sales) {
            for (SaleItem item : saleItemRepository.findBySaleId(sale.getSaleId())) {
                Product product = productMap.get(item.getProductId());
                // SaleItem has no stored cost field; product buyingPrice is the safest available COGS source.
                BigDecimal buyingPrice = product != null ? zero(product.getBuyingPrice()) : BigDecimal.ZERO;
                totalCost = totalCost.add(buyingPrice.multiply(BigDecimal.valueOf(zero(item.getQuantity()))));
            }
        }
        return totalCost;
    }

    private List<Map<String, Object>> revenueBreakdown(List<Sale> sales, BigDecimal totalRevenue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", "Sales Revenue");
        row.put("count", sales.size());
        row.put("amount", totalRevenue);
        row.put("percentage", percentage(totalRevenue, totalRevenue));
        return totalRevenue.compareTo(BigDecimal.ZERO) > 0 ? List.of(row) : List.of();
    }

    private List<Map<String, Object>> purchaseBreakdown(BigDecimal cogs, int salesCount) {
        if (cogs.compareTo(BigDecimal.ZERO) <= 0) {
            return List.of();
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", "Cost of Goods Sold");
        row.put("count", salesCount);
        row.put("amount", cogs);
        row.put("percentage", BigDecimal.valueOf(100));
        return List.of(row);
    }

    private List<Map<String, Object>> expenseBreakdown(List<Expense> expenses, BigDecimal totalExpenses) {
        if (expenses.isEmpty()) {
            return List.of();
        }

        Map<Long, ExpenseCategory> categories = expenseCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(ExpenseCategory::getExpCategoryId, Function.identity(), (a, b) -> a));
        Map<String, CategoryTotal> totals = new LinkedHashMap<>();

        for (Expense expense : expenses) {
            List<ExpenseItem> items = expenseItemRepository.findByExpense_ExpenseId(expense.getExpenseId());
            if (items.isEmpty()) {
                totals.computeIfAbsent("Uncategorized", key -> new CategoryTotal()).add(zero(expense.getGrandTotal()));
                continue;
            }
            for (ExpenseItem item : items) {
                ExpenseCategory category = categories.get(item.getExpCategoryId());
                String name = category != null && category.getCategoryName() != null ? category.getCategoryName() : "Uncategorized";
                totals.computeIfAbsent(name, key -> new CategoryTotal()).add(zero(item.getTotalPrice()));
            }
        }

        return totals.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("categoryName", entry.getKey());
                    row.put("count", entry.getValue().count);
                    row.put("amount", entry.getValue().amount);
                    row.put("percentage", percentage(entry.getValue().amount, totalExpenses));
                    return row;
                })
                .sorted((a, b) -> ((BigDecimal) b.get("amount")).compareTo((BigDecimal) a.get("amount")))
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> profitTrend(LocalDate startDate, LocalDate endDate, Map<Long, Product> productMap) {
        List<PeriodBucket> buckets = buildBuckets(startDate, endDate);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (PeriodBucket bucket : buckets) {
            List<Sale> sales = saleRepository.findCompletedSalesBetween(bucket.start.atStartOfDay(), bucket.end.atTime(LocalTime.MAX));
            List<Expense> expenses = expenseRepository.findApprovedBetween(bucket.start, bucket.end);
            BigDecimal revenue = sales.stream().map(sale -> zero(sale.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cogs = calculateCogs(sales, productMap);
            BigDecimal expenseTotal = expenses.stream().map(expense -> zero(expense.getGrandTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal net = revenue.subtract(cogs).subtract(expenseTotal);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", bucket.label);
            row.put("revenue", revenue);
            row.put("expenses", expenseTotal);
            row.put("netProfit", net);
            trend.add(row);
        }
        return trend;
    }

    private List<PeriodBucket> buildBuckets(LocalDate startDate, LocalDate endDate) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        List<PeriodBucket> buckets = new ArrayList<>();
        if (days <= 62) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                buckets.add(new PeriodBucket(date.format(formatter), date, date));
            }
            return buckets;
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        LocalDate cursor = startDate.withDayOfMonth(1);
        while (!cursor.isAfter(endDate)) {
            LocalDate bucketStart = cursor.isBefore(startDate) ? startDate : cursor;
            LocalDate monthEnd = cursor.withDayOfMonth(cursor.lengthOfMonth());
            LocalDate bucketEnd = monthEnd.isAfter(endDate) ? endDate : monthEnd;
            buckets.add(new PeriodBucket(cursor.format(formatter), bucketStart, bucketEnd));
            cursor = cursor.plusMonths(1);
        }
        return buckets;
    }

    private List<String> insights(BigDecimal revenue, BigDecimal expenses, BigDecimal netProfit) {
        List<String> messages = new ArrayList<>();
        if (revenue.compareTo(BigDecimal.ZERO) == 0) {
            messages.add("No revenue found for this period.");
            return messages;
        }
        if (netProfit.compareTo(BigDecimal.ZERO) > 0) {
            messages.add("Business is profitable for this period.");
        } else if (netProfit.compareTo(BigDecimal.ZERO) < 0) {
            messages.add("Business is running at a loss for this period.");
        } else {
            messages.add("Business broke even for this period.");
        }
        if (percentage(expenses, revenue).compareTo(BigDecimal.valueOf(60)) >= 0) {
            messages.add("Expenses are high compared to revenue.");
        }
        return messages;
    }

    private BigDecimal margin(BigDecimal value, BigDecimal revenue) {
        return percentage(value, revenue);
    }

    private BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int zero(Integer value) {
        return value == null ? 0 : value;
    }

    private static class CategoryTotal {
        private BigDecimal amount = BigDecimal.ZERO;
        private int count = 0;

        private void add(BigDecimal value) {
            amount = amount.add(value);
            count++;
        }
    }

    private static class PeriodBucket {
        private final String label;
        private final LocalDate start;
        private final LocalDate end;

        private PeriodBucket(String label, LocalDate start, LocalDate end) {
            this.label = label;
            this.start = start;
            this.end = end;
        }
    }
}
