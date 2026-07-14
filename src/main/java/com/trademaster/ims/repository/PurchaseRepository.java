package com.trademaster.ims.repository;

import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.model.Purchase.PurchaseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
//import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    
    // Find all with pagination
    Page<Purchase> findAll(Pageable pageable);
    
    // Find by status with pagination
    Page<Purchase> findByStatus(PurchaseStatus status, Pageable pageable);
    
    // Search by purchase order number (case-insensitive)
    @Query("SELECT p FROM Purchase p WHERE LOWER(p.purchaseOrderNo) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Purchase> searchByOrderNo(@Param("keyword") String keyword, Pageable pageable);
    
    // ✅ ADDED: Find by exact purchase order number
    Optional<Purchase> findByPurchaseOrderNo(String purchaseOrderNo);
    
    // Find by supplier
    Page<Purchase> findBySupplierId(Long supplierId, Pageable pageable);
    
    // Find by warehouse
    Page<Purchase> findByWarehouseId(Long warehouseId, Pageable pageable);
    
    // Find by date range
    Page<Purchase> findByPurchaseDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);
    
    // Find pending purchases with expected delivery before date
    List<Purchase> findByStatusAndExpectedDeliveryBefore(PurchaseStatus status, LocalDate date);
    
    // Find by supplier and status
    Page<Purchase> findBySupplierIdAndStatus(Long supplierId, PurchaseStatus status, Pageable pageable);
    
    // Find by warehouse and status
    Page<Purchase> findByWarehouseIdAndStatus(Long warehouseId, PurchaseStatus status, Pageable pageable);
    
    // Check if purchase order number exists
    boolean existsByPurchaseOrderNo(String purchaseOrderNo);
    
    // Check if purchase order number exists for other purchase (for update)
    @Query("SELECT COUNT(p) > 0 FROM Purchase p WHERE p.purchaseOrderNo = :orderNo AND p.purchaseId != :id")
    boolean existsByPurchaseOrderNoAndPurchaseIdNot(@Param("orderNo") String orderNo, @Param("id") Long id);
    
    // Count purchases by status
    long countByStatus(PurchaseStatus status);
    
    // Get total purchase amount by supplier
    @Query("SELECT SUM(p.totalAmount) FROM Purchase p WHERE p.supplierId = :supplierId AND p.status = 'RECEIVED'")
    java.math.BigDecimal getTotalPurchaseAmountBySupplier(@Param("supplierId") Long supplierId);
    
    // Get recent purchases (last N days)
    @Query("SELECT p FROM Purchase p WHERE p.purchaseDate >= :date ORDER BY p.purchaseDate DESC")
    List<Purchase> findRecentPurchases(@Param("date") LocalDate date);
    
    // purchases reports method 
    List<Purchase> findAllByPurchaseDateBetween(LocalDate startDate, LocalDate endDate);

    @Query("SELECT p FROM Purchase p WHERE p.status = 'RECEIVED' AND p.purchaseDate BETWEEN :startDate AND :endDate ORDER BY p.purchaseDate DESC")
    List<Purchase> findReceivedPurchasesBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(p.taxAmount), 0) FROM Purchase p WHERE p.status = 'RECEIVED' AND p.purchaseDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalTaxBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    Page<Purchase> findByOrderByPurchaseDateDesc(Pageable pageable);
}
