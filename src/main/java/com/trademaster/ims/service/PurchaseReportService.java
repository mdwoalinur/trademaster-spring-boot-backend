
package com.trademaster.ims.service;

import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.repository.PurchaseRepository;
import com.trademaster.ims.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PurchaseReportService {

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    public Map<String, Object> getPurchaseReport(LocalDate startDate, LocalDate endDate) {
        // Build a map of supplierId -> supplierName for fast lookup
        Map<Long, String> supplierNameMap = supplierRepository.findAll().stream()
                .collect(Collectors.toMap(
                    s -> s.getSupplierId(),
                    s -> s.getSupplierName(),
                    (existing, replacement) -> existing
                ));

        // Fetch all purchases within date range (no status filter, show all)
        List<Purchase> purchases = purchaseRepository.findAll().stream()
                .filter(p -> p.getPurchaseDate() != null &&
                        !p.getPurchaseDate().isBefore(startDate) &&
                        !p.getPurchaseDate().isAfter(endDate))
                .collect(Collectors.toList());

        // But the above is inefficient; better to use repository query.
        // We'll assume we have a method in PurchaseRepository:
        // List<Purchase> findAllByPurchaseDateBetween(LocalDate start, LocalDate end);
        // We'll inject it. We'll add that method if not present.
        // For now, we will use the repository method directly from repository.
        List<Purchase> purchasesInRange = purchaseRepository.findAllByPurchaseDateBetween(startDate, endDate);

        // Calculate totals
        long totalCount = purchasesInRange.size();
        BigDecimal totalAmount = purchasesInRange.stream()
                .map(p -> p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Enrich with supplier name
        List<Map<String, Object>> purchaseList = new ArrayList<>();
        for (Purchase p : purchasesInRange) {
            Map<String, Object> item = new HashMap<>();
            item.put("purchaseId", p.getPurchaseId());
            item.put("purchaseOrderNo", p.getPurchaseOrderNo());
            item.put("supplierId", p.getSupplierId());
            item.put("supplierName", supplierNameMap.getOrDefault(p.getSupplierId(), "Unknown"));
            item.put("purchaseDate", p.getPurchaseDate());
            item.put("totalAmount", p.getTotalAmount());
            item.put("status", p.getStatus() != null ? p.getStatus().name() : null);
            purchaseList.add(item);
        }

        // Group by supplier: supplier-wise total
        Map<String, BigDecimal> supplierTotals = new HashMap<>();
        for (Map<String, Object> p : purchaseList) {
            String name = (String) p.get("supplierName");
            BigDecimal amount = (BigDecimal) p.get("totalAmount");
            supplierTotals.merge(name, amount, BigDecimal::add);
        }
        List<Map<String, Object>> supplierSummary = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : supplierTotals.entrySet()) {
            Map<String, Object> s = new HashMap<>();
            s.put("supplierName", entry.getKey());
            s.put("totalAmount", entry.getValue());
            supplierSummary.add(s);
        }
        // Sort by amount descending
        supplierSummary.sort((a, b) -> ((BigDecimal) b.get("totalAmount")).compareTo((BigDecimal) a.get("totalAmount")));

        Map<String, Object> report = new HashMap<>();
        report.put("totalCount", totalCount);
        report.put("totalAmount", totalAmount);
        report.put("purchases", purchaseList);
        report.put("supplierSummary", supplierSummary);
        return report;
    }
}