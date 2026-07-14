package com.trademaster.ims.repository;

import com.trademaster.ims.model.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    List<EmailLog> findByEmailTypeAndReferenceIdOrderByCreatedAtDesc(EmailLog.EmailType emailType, Long referenceId);
}
