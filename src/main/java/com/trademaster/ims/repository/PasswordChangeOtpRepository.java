package com.trademaster.ims.repository;

import com.trademaster.ims.model.PasswordChangeOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasswordChangeOtpRepository extends JpaRepository<PasswordChangeOtp, Long> {
    Optional<PasswordChangeOtp> findTopByUserIdAndUsedFalseOrderByCreatedAtDesc(Long userId);
    List<PasswordChangeOtp> findByUserIdAndUsedFalse(Long userId);
    Optional<PasswordChangeOtp> findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(Long userId, String purpose);
    List<PasswordChangeOtp> findByUserIdAndPurposeAndUsedFalse(Long userId, String purpose);
}
