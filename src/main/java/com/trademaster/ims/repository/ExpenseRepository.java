package com.trademaster.ims.repository;

import com.trademaster.ims.model.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    
    Page<Expense> findAll(Pageable pageable);
    
    Page<Expense> findByStatus(Expense.ExpenseStatus status, Pageable pageable);
    
    Page<Expense> findByPaymentStatus(Expense.PaymentStatus status, Pageable pageable);
    
    @Query("SELECT e FROM Expense e WHERE LOWER(e.expenseNo) LIKE %:keyword% OR LOWER(e.notes) LIKE %:keyword%")
    Page<Expense> search(@Param("keyword") String keyword, Pageable pageable);
    
    Optional<Expense> findByExpenseNo(String expenseNo);
    
    boolean existsByExpenseNo(String expenseNo);
    
    // Fetch vendor with items (used when vendor details are needed)
    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.vendor LEFT JOIN FETCH e.items WHERE e.expenseId = :id")
    Optional<Expense> findByIdWithVendor(@Param("id") Long id);
    
    // Fetch attachments separately (used when only attachments are required)
    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.attachments WHERE e.expenseId = :id")
    Optional<Expense> findByIdWithAttachments(@Param("id") Long id);
    
    /**
     * Fetch expense with items and attachments together.
     * This works without MultipleBagFetchException because 'attachments' is now a Set.
     * Vendor is not fetched here to avoid additional complexity; frontend uses vendorMap.
     */
    @Query("SELECT e FROM Expense e LEFT JOIN FETCH e.items LEFT JOIN FETCH e.attachments WHERE e.expenseId = :id")
    Optional<Expense> findExpenseWithItemsAndAttachments(@Param("id") Long id);
    
 // ✅ Correct: Use @Query for Expense as well
    @Query("SELECT COALESCE(SUM(e.grandTotal), 0) FROM Expense e WHERE e.status = 'APPROVED' AND e.expenseDate BETWEEN :startDate AND :endDate")
    BigDecimal getTotalExpensesBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT e FROM Expense e WHERE e.status = 'APPROVED' AND e.expenseDate BETWEEN :startDate AND :endDate")
    List<Expense> findApprovedBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(e) FROM Expense e WHERE e.status = 'APPROVED' AND e.expenseDate BETWEEN :startDate AND :endDate")
    Long countApprovedBetween(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    
}
