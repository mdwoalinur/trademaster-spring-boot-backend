package com.trademaster.ims.repository;

import com.trademaster.ims.model.PaymentApprovalHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentApprovalHistoryRepository extends JpaRepository<PaymentApprovalHistory, Long> {
    List<PaymentApprovalHistory> findByPaymentPaymentIdOrderByActedAtAsc(Long paymentId);
}
