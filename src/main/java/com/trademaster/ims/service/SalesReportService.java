
package com.trademaster.ims.service;

import com.trademaster.ims.model.Sale;
import com.trademaster.ims.model.Customer;
import com.trademaster.ims.repository.CustomerRepository;
import com.trademaster.ims.repository.SaleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class SalesReportService {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private CustomerRepository customerRepository;

    public Map<String, Object> getSalesReport(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<Sale> sales = saleRepository.findCompletedSalesBetween(start, end);
        Long totalCount = saleRepository.countCompletedSalesBetween(start, end);
        BigDecimal totalSubtotal = saleRepository.getTotalSubtotalBetween(start, end);
        BigDecimal totalTax = saleRepository.getTotalTaxBetween(start, end);
        BigDecimal totalDiscount = saleRepository.getTotalDiscountBetween(start, end);
        BigDecimal totalRevenue = saleRepository.getTotalSalesRevenueBetween(start, end);

        Map<Long, Customer> customers = customerRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Customer::getCustomerId, c -> c, (a, b) -> a));
        List<Map<String, Object>> saleRows = new ArrayList<>();
        for (Sale sale : sales) {
            Customer customer = customers.get(sale.getCustomerId());
            Map<String, Object> row = new HashMap<>();
            row.put("saleId", sale.getSaleId());
            row.put("invoiceNo", sale.getInvoiceNo());
            row.put("customerId", sale.getCustomerId());
            row.put("customerName", customer != null ? customer.getCustomerName() : "Customer #" + sale.getCustomerId());
            row.put("customerCode", customer != null ? customer.getCustomerCode() : "");
            row.put("customerPhotoUrl", customer != null ? customer.getPhotoUrl() : "");
            row.put("warehouseId", sale.getWarehouseId());
            row.put("saleDate", sale.getSaleDate());
            row.put("subtotal", sale.getSubtotal());
            row.put("taxAmount", sale.getTaxAmount());
            row.put("discountAmount", sale.getDiscountAmount());
            row.put("totalAmount", sale.getTotalAmount());
            row.put("paidAmount", sale.getPaidAmount());
            row.put("dueAmount", sale.getDueAmount());
            row.put("paymentStatus", sale.getPaymentStatus());
            row.put("status", sale.getStatus());
            saleRows.add(row);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("sales", saleRows);
        report.put("totalCount", totalCount);
        report.put("totalSubtotal", totalSubtotal != null ? totalSubtotal : BigDecimal.ZERO);
        report.put("totalTax", totalTax != null ? totalTax : BigDecimal.ZERO);
        report.put("totalDiscount", totalDiscount != null ? totalDiscount : BigDecimal.ZERO);
        report.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        return report;
    }
}
