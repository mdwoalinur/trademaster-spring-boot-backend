package com.trademaster.ims.repository;

import com.trademaster.ims.model.PaymentAllocation;
import com.trademaster.ims.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPaymentPaymentId(Long paymentId);

    List<PaymentAllocation> findByReferenceTypeAndReferenceId(PaymentAllocation.ReferenceType referenceType, Long referenceId);

    @Query("SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM PaymentAllocation a " +
           "WHERE a.referenceType = :referenceType AND a.referenceId = :referenceId " +
           "AND a.payment.approvalStatus = :approvalStatus " +
           "AND a.payment.transactionStatus = :transactionStatus")
    BigDecimal getAmountForReferenceByStatus(@Param("referenceType") PaymentAllocation.ReferenceType referenceType,
                                             @Param("referenceId") Long referenceId,
                                             @Param("approvalStatus") Payment.PaymentApprovalStatus approvalStatus,
                                             @Param("transactionStatus") Payment.PaymentTransactionStatus transactionStatus);

    @Query("SELECT COALESCE(SUM(a.allocatedAmount), 0) FROM PaymentAllocation a " +
           "WHERE a.referenceType = :referenceType AND a.referenceId = :referenceId " +
           "AND a.payment.approvalStatus = :approvalStatus " +
           "AND a.payment.transactionStatus = :transactionStatus " +
           "AND (:paymentId IS NULL OR a.payment.paymentId <> :paymentId)")
    BigDecimal getAmountForReferenceByStatusExcludingPayment(@Param("referenceType") PaymentAllocation.ReferenceType referenceType,
                                                             @Param("referenceId") Long referenceId,
                                                             @Param("approvalStatus") Payment.PaymentApprovalStatus approvalStatus,
                                                             @Param("transactionStatus") Payment.PaymentTransactionStatus transactionStatus,
                                                             @Param("paymentId") Long paymentId);

    default BigDecimal getPostedAmountForReference(PaymentAllocation.ReferenceType referenceType, Long referenceId) {
        return getAmountForReferenceByStatus(referenceType, referenceId,
                Payment.PaymentApprovalStatus.APPROVED,
                Payment.PaymentTransactionStatus.POSTED);
    }

    default BigDecimal getPendingAmountForReference(PaymentAllocation.ReferenceType referenceType, Long referenceId) {
        return getAmountForReferenceByStatus(referenceType, referenceId,
                Payment.PaymentApprovalStatus.PENDING_APPROVAL,
                Payment.PaymentTransactionStatus.PENDING);
    }

    default BigDecimal getPostedAmountForReferenceExcludingPayment(PaymentAllocation.ReferenceType referenceType, Long referenceId, Long paymentId) {
        return getAmountForReferenceByStatusExcludingPayment(referenceType, referenceId,
                Payment.PaymentApprovalStatus.APPROVED,
                Payment.PaymentTransactionStatus.POSTED,
                paymentId);
    }

    default BigDecimal getPendingAmountForReferenceExcludingPayment(PaymentAllocation.ReferenceType referenceType, Long referenceId, Long paymentId) {
        return getAmountForReferenceByStatusExcludingPayment(referenceType, referenceId,
                Payment.PaymentApprovalStatus.PENDING_APPROVAL,
                Payment.PaymentTransactionStatus.PENDING,
                paymentId);
    }
}
