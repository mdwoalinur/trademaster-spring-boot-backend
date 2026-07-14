package com.trademaster.ims.service;

import com.trademaster.ims.model.Customer;
import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.model.PurchaseItem;
import com.trademaster.ims.model.Sale;
import com.trademaster.ims.model.SaleItem;
import com.trademaster.ims.model.Supplier;
import com.trademaster.ims.repository.CustomerRepository;
import com.trademaster.ims.repository.PurchaseItemRepository;
import com.trademaster.ims.repository.PurchaseRepository;
import com.trademaster.ims.repository.SaleItemRepository;
import com.trademaster.ims.repository.SaleRepository;
import com.trademaster.ims.repository.SupplierRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaxReportService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final CustomerRepository customerRepository;
    private final SupplierRepository supplierRepository;

    public TaxReportService(SaleRepository saleRepository,
                            SaleItemRepository saleItemRepository,
                            PurchaseRepository purchaseRepository,
                            PurchaseItemRepository purchaseItemRepository,
                            CustomerRepository customerRepository,
                            SupplierRepository supplierRepository) {
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
        this.purchaseRepository = purchaseRepository;
        this.purchaseItemRepository = purchaseItemRepository;
        this.customerRepository = customerRepository;
        this.supplierRepository = supplierRepository;
    }

    public Map<String, Object> getTaxReport(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = LocalDate.now();
        if (endDate.isBefore(startDate)) {
            LocalDate temp = startDate;
            startDate = endDate;
            endDate = temp;
        }

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);
        List<Sale> sales = saleRepository.findCompletedSalesBetween(startDateTime, endDateTime);
        List<Purchase> purchases = purchaseRepository.findReceivedPurchasesBetween(startDate, endDate);
        Map<Long, Customer> customers = customerRepository.findAll().stream()
                .collect(Collectors.toMap(Customer::getCustomerId, Function.identity(), (a, b) -> a));
        Map<Long, Supplier> suppliers = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(Supplier::getSupplierId, Function.identity(), (a, b) -> a));

        BigDecimal totalSalesAmount = sales.stream().map(s -> zero(s.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal salesTaxOutput = sales.stream().map(s -> zero(s.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPurchaseAmount = purchases.stream().map(p -> zero(p.getTotalAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal purchaseTaxInput = purchases.stream().map(p -> zero(p.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal netTaxPayable = salesTaxOutput.subtract(purchaseTaxInput);
        BigDecimal refundableAmount = netTaxPayable.signum() < 0 ? netTaxPayable.abs() : BigDecimal.ZERO;
        BigDecimal averageTaxRate = averageTaxRate(salesTaxOutput.add(purchaseTaxInput),
                sales.stream().map(s -> zero(s.getSubtotal())).reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(purchases.stream().map(p -> zero(p.getSubtotal())).reduce(BigDecimal.ZERO, BigDecimal::add)));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("totalSalesAmount", totalSalesAmount);
        report.put("salesTaxOutput", salesTaxOutput);
        report.put("totalPurchaseAmount", totalPurchaseAmount);
        report.put("purchaseTaxInput", purchaseTaxInput);
        report.put("netTaxPayable", netTaxPayable);
        report.put("refundableAmount", refundableAmount);
        report.put("salesInvoiceCount", sales.size());
        report.put("purchaseInvoiceCount", purchases.size());
        report.put("averageTaxRate", averageTaxRate);
        report.put("salesTaxBreakdown", salesTaxBreakdown(sales, customers));
        report.put("purchaseTaxBreakdown", purchaseTaxBreakdown(purchases, suppliers));
        report.put("taxSummaryByRate", taxSummaryByRate(sales, purchases));
        report.put("taxTrend", taxTrend(startDate, endDate));
        report.put("insights", insights(salesTaxOutput, purchaseTaxInput, netTaxPayable, sales.size(), purchases.size()));

        report.put("salesTax", salesTaxOutput);
        report.put("purchaseTax", purchaseTaxInput);
        report.put("netTax", netTaxPayable);
        return report;
    }

    private List<Map<String, Object>> salesTaxBreakdown(List<Sale> sales, Map<Long, Customer> customers) {
        return sales.stream()
                .sorted(Comparator.comparing(Sale::getSaleDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(sale -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Customer customer = customers.get(sale.getCustomerId());
                    BigDecimal taxableAmount = zero(sale.getSubtotal());
                    BigDecimal outputVat = zero(sale.getTaxAmount());
                    row.put("invoiceNo", safe(sale.getInvoiceNo()));
                    row.put("saleDate", sale.getSaleDate() == null ? null : sale.getSaleDate().toLocalDate());
                    row.put("customerName", customer == null ? "N/A" : safe(customer.getCustomerName()));
                    row.put("taxableAmount", taxableAmount);
                    row.put("taxRate", averageTaxRate(outputVat, taxableAmount));
                    row.put("outputVat", outputVat);
                    row.put("totalAmount", zero(sale.getTotalAmount()));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> purchaseTaxBreakdown(List<Purchase> purchases, Map<Long, Supplier> suppliers) {
        return purchases.stream()
                .sorted(Comparator.comparing(Purchase::getPurchaseDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(purchase -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    Supplier supplier = suppliers.get(purchase.getSupplierId());
                    BigDecimal taxableAmount = zero(purchase.getSubtotal());
                    BigDecimal inputVat = zero(purchase.getTaxAmount());
                    row.put("purchaseOrderNo", safe(purchase.getPurchaseOrderNo()));
                    row.put("purchaseDate", purchase.getPurchaseDate());
                    row.put("supplierName", supplier == null ? "N/A" : safe(supplier.getSupplierName()));
                    row.put("taxableAmount", taxableAmount);
                    row.put("taxRate", averageTaxRate(inputVat, taxableAmount));
                    row.put("inputVat", inputVat);
                    row.put("totalAmount", zero(purchase.getTotalAmount()));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> taxSummaryByRate(List<Sale> sales, List<Purchase> purchases) {
        Map<String, RateTotal> totals = new HashMap<>();
        for (Sale sale : sales) {
            for (SaleItem item : saleItemRepository.findBySaleId(sale.getSaleId())) {
                BigDecimal rate = zero(item.getTaxRate());
                BigDecimal taxableAmount = itemTaxableAmount(item);
                BigDecimal outputVat = taxableAmount.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                totals.computeIfAbsent(rateKey(rate), key -> new RateTotal(rate)).addSale(taxableAmount, outputVat);
            }
        }
        for (Purchase purchase : purchases) {
            for (PurchaseItem item : purchaseItemRepository.findByPurchase_PurchaseId(purchase.getPurchaseId())) {
                BigDecimal rate = zero(item.getTax());
                BigDecimal taxableAmount = zero(item.getSubtotal());
                BigDecimal inputVat = zero(item.getTaxAmount());
                totals.computeIfAbsent(rateKey(rate), key -> new RateTotal(rate)).addPurchase(taxableAmount, inputVat);
            }
        }
        return totals.values().stream()
                .sorted(Comparator.comparing(RateTotal::getRate))
                .map(total -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("taxRate", total.rate);
                    row.put("salesTaxableAmount", total.salesTaxableAmount);
                    row.put("outputVat", total.outputVat);
                    row.put("purchaseTaxableAmount", total.purchaseTaxableAmount);
                    row.put("inputVat", total.inputVat);
                    row.put("netVat", total.outputVat.subtract(total.inputVat));
                    return row;
                })
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> taxTrend(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PeriodBucket bucket : buildBuckets(startDate, endDate)) {
            List<Sale> sales = saleRepository.findCompletedSalesBetween(bucket.start.atStartOfDay(), bucket.end.atTime(23, 59, 59));
            List<Purchase> purchases = purchaseRepository.findReceivedPurchasesBetween(bucket.start, bucket.end);
            BigDecimal outputVat = sales.stream().map(s -> zero(s.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal inputVat = purchases.stream().map(p -> zero(p.getTaxAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("label", bucket.label);
            row.put("outputVat", outputVat);
            row.put("inputVat", inputVat);
            row.put("netVat", outputVat.subtract(inputVat));
            rows.add(row);
        }
        return rows;
    }

    private List<String> insights(BigDecimal outputVat, BigDecimal inputVat, BigDecimal netVat, int salesCount, int purchaseCount) {
        List<String> messages = new ArrayList<>();
        if (salesCount == 0 && purchaseCount == 0) {
            messages.add("No tax data found for selected date range.");
            return messages;
        }
        if (netVat.signum() > 0) {
            messages.add("Tax payable to government for this period.");
        } else if (netVat.signum() < 0) {
            messages.add("Input tax is higher than output tax. Amount may be refundable or carried forward.");
        } else {
            messages.add("No taxable transaction found.");
        }
        if (outputVat.compareTo(inputVat.multiply(BigDecimal.valueOf(2))) > 0 && outputVat.signum() > 0) {
            messages.add("High sales tax generated from sales transactions.");
        }
        return messages;
    }

    private List<PeriodBucket> buildBuckets(LocalDate startDate, LocalDate endDate) {
        List<PeriodBucket> buckets = new ArrayList<>();
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (days <= 62) {
            LocalDate cursor = startDate;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd MMM");
            while (!cursor.isAfter(endDate)) {
                buckets.add(new PeriodBucket(cursor.format(formatter), cursor, cursor));
                cursor = cursor.plusDays(1);
            }
            return buckets;
        }
        YearMonth cursor = YearMonth.from(startDate);
        YearMonth end = YearMonth.from(endDate);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
        while (!cursor.isAfter(end)) {
            LocalDate bucketStart = cursor.atDay(1).isBefore(startDate) ? startDate : cursor.atDay(1);
            LocalDate bucketEnd = cursor.atEndOfMonth().isAfter(endDate) ? endDate : cursor.atEndOfMonth();
            buckets.add(new PeriodBucket(cursor.format(formatter), bucketStart, bucketEnd));
            cursor = cursor.plusMonths(1);
        }
        return buckets;
    }

    private BigDecimal itemTaxableAmount(SaleItem item) {
        BigDecimal gross = zero(item.getUnitPrice()).multiply(BigDecimal.valueOf(item.getQuantity() == null ? 0 : item.getQuantity()));
        return gross.subtract(zero(item.getDiscountAmount())).max(BigDecimal.ZERO);
    }

    private BigDecimal averageTaxRate(BigDecimal tax, BigDecimal taxableAmount) {
        if (taxableAmount == null || taxableAmount.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return zero(tax).multiply(BigDecimal.valueOf(100)).divide(taxableAmount, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String rateKey(BigDecimal rate) {
        return zero(rate).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static class RateTotal {
        private final BigDecimal rate;
        private BigDecimal salesTaxableAmount = BigDecimal.ZERO;
        private BigDecimal outputVat = BigDecimal.ZERO;
        private BigDecimal purchaseTaxableAmount = BigDecimal.ZERO;
        private BigDecimal inputVat = BigDecimal.ZERO;

        private RateTotal(BigDecimal rate) {
            this.rate = rate == null ? BigDecimal.ZERO : rate;
        }

        private BigDecimal getRate() {
            return rate;
        }

        private void addSale(BigDecimal taxableAmount, BigDecimal vat) {
            salesTaxableAmount = salesTaxableAmount.add(taxableAmount == null ? BigDecimal.ZERO : taxableAmount);
            outputVat = outputVat.add(vat == null ? BigDecimal.ZERO : vat);
        }

        private void addPurchase(BigDecimal taxableAmount, BigDecimal vat) {
            purchaseTaxableAmount = purchaseTaxableAmount.add(taxableAmount == null ? BigDecimal.ZERO : taxableAmount);
            inputVat = inputVat.add(vat == null ? BigDecimal.ZERO : vat);
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
