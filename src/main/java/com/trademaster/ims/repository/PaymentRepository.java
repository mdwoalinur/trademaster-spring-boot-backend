
package com.trademaster.ims.repository;

import com.trademaster.ims.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findAll(Pageable pageable);

    Page<Payment> findByPaymentType(Payment.PaymentType type, Pageable pageable);

    Page<Payment> findByPaymentStatus(Payment.PaymentStatus status, Pageable pageable);

    Page<Payment> findByApprovalStatus(Payment.PaymentApprovalStatus status, Pageable pageable);

    Page<Payment> findByTransactionStatus(Payment.PaymentTransactionStatus status, Pageable pageable);

    Page<Payment> findByPaymentMethod(Payment.PaymentMethod method, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentId = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT p FROM Payment p WHERE " +
           "LOWER(COALESCE(p.voucherNo, '')) LIKE %:keyword% OR " +
           "LOWER(COALESCE(p.referenceNo, '')) LIKE %:keyword% OR " +
           "LOWER(COALESCE(p.notes, '')) LIKE %:keyword%")
    Page<Payment> search(@Param("keyword") String keyword, Pageable pageable);

    // Sum of amounts by type and status
    @Query("SELECT COALESCE(SUM(p.approvedAmount), 0) FROM Payment p WHERE p.paymentType = :type AND p.approvalStatus = 'APPROVED' AND p.transactionStatus = 'POSTED'")
    BigDecimal getTotalPaidByType(@Param("type") Payment.PaymentType type);

    // Payments between dates
    Page<Payment> findByPaymentDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    List<Payment> findByOriginalPaymentId(Long originalPaymentId);
}

