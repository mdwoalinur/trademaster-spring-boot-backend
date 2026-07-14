package com.trademaster.ims.repository;

import com.trademaster.ims.model.PaymentAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentAttachmentRepository extends JpaRepository<PaymentAttachment, Long> {
    List<PaymentAttachment> findByPaymentIdAndDeletedFalseOrderByUploadedAtDesc(Long paymentId);
    Optional<PaymentAttachment> findByAttachmentIdAndPaymentIdAndDeletedFalse(Long attachmentId, Long paymentId);
}
