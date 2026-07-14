package com.trademaster.ims.repository;

import com.trademaster.ims.model.Sale;


import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {
    
    // Find by customer
    List<Sale> findByCustomerId(Long customerId);
    
    // Find by warehouse
    List<Sale> findByWarehouseId(Long warehouseId);
    
    // Find by status
    List<Sale> findByStatus(Sale.SaleStatus status);
    
    // Find by payment status
    List<Sale> findByPaymentStatus(Sale.PaymentStatus paymentStatus);
    
    // Find by invoice number
    List<Sale> findByInvoiceNoContaining(String invoiceNo);
    
    // Find by date range
    List<Sale> findBySaleDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    // Find sales with due amount
    List<Sale> findByDueAmountGreaterThan(java.math.BigDecimal zero);
    
 // Use @Query to define the JPQL
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSalesAmountBetween(@Param("startDate") LocalDateTime localDateTime, @Param("endDate") LocalDateTime localDateTime2);
    
 // sales report query 

    @Query("SELECT s FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate ORDER BY s.saleDate DESC")
    List<Sale> findCompletedSalesBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(s) FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate")
    Long countCompletedSalesBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(s.subtotal), 0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSubtotalBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(s.taxAmount), 0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalTaxBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(s.discountAmount), 0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalDiscountBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.status = 'COMPLETED' AND s.saleDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalSalesRevenueBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    
    @Query("SELECT new map(s.customerId as customerId, " +
    	       "COALESCE(c.customerName, 'Walk-in') as customerName, " +
    	       "c.customerCode as customerCode, " +
    	       "c.photoUrl as photoUrl, " +
    	       "SUM(s.totalAmount) as totalSpent) " +
    	       "FROM Sale s LEFT JOIN Customer c ON s.customerId = c.customerId " +
    	       "WHERE s.customerId IS NOT NULL " +
    	       "GROUP BY s.customerId, c.customerName, c.customerCode, c.photoUrl " +
    	       "ORDER BY totalSpent DESC")
    	List<Map<String, Object>> findTopCustomers(Pageable pageable);
    
    Page<Sale> findByOrderBySaleDateDesc(Pageable pageable);
    
    
}
