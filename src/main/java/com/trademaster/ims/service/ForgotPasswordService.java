package com.trademaster.ims.service;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.PasswordChangeOtp;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.PasswordChangeOtpRepository;
import com.trademaster.ims.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ForgotPasswordService {
    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);
    private static final String PURPOSE = "FORGOT_PASSWORD";
    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int RESEND_COOLDOWN_SECONDS = 45;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final String SAFE_SENT_MESSAGE = "If an active account matches the provided information, a verification code will be sent.";

    private final UserRepository userRepository;
    private final PasswordChangeOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    public ForgotPasswordService(
            UserRepository userRepository,
            PasswordChangeOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @Transactional(noRollbackFor = ApiResponseException.class)
    public Map<String, Object> requestOtp(Map<String, String> request) {
        Optional<User> userOpt = findActiveUser(request.getOrDefault("emailOrUsername", ""));
        if (userOpt.isEmpty()) {
            return Map.of("message", SAFE_SENT_MESSAGE);
        }

        User user = userOpt.get();
        String email = requireEmail(user);
        validateMailConfiguration();
        invalidateOldOtps(user.getUserId());

        PasswordChangeOtp otp = new PasswordChangeOtp();
        otp.setUserId(user.getUserId());
        otp.setEmail(email);
        otp.setOtpCode(generateOtp());
        otp.setPurpose(PURPOSE);
        otp.setEncodedNewPassword("");
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otp.setVerified(false);
        otp.setVerificationAttempts(0);
        otp.setLastSentAt(LocalDateTime.now());
        otpRepository.save(otp);

        sendOtpEmail(user, otp);
        return Map.of("message", SAFE_SENT_MESSAGE, "maskedEmail", maskEmail(email));
    }

    @Transactional(noRollbackFor = ApiResponseException.class)
    public Map<String, Object> resendOtp(Map<String, String> request) {
        User user = findActiveUser(request.getOrDefault("emailOrUsername", ""))
                .orElseThrow(() -> new IllegalArgumentException("No active password reset request found"));
        PasswordChangeOtp otp = latestOtp(user.getUserId());
        validateActiveOtp(otp);

        if (otp.getLastSentAt() != null && otp.getLastSentAt().plusSeconds(RESEND_COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
            throw new ApiResponseException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another verification code");
        }

        otp.setOtpCode(generateOtp());
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(OTP_EXPIRY_MINUTES));
        otp.setVerified(false);
        otp.setVerificationAttempts(0);
        otp.setLastSentAt(LocalDateTime.now());
        otpRepository.save(otp);

        sendOtpEmail(user, otp);
        return Map.of("message", "Verification code has been resent.", "maskedEmail", maskEmail(otp.getEmail()));
    }

    @Transactional
    public Map<String, Object> verifyOtp(Map<String, String> request) {
        User user = findActiveUser(request.getOrDefault("emailOrUsername", ""))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));
        PasswordChangeOtp otp = latestOtp(user.getUserId());
        validateActiveOtp(otp);

        String code = cleanOtp(request.getOrDefault("otp", ""));
        if (!otp.getOtpCode().equals(code)) {
            int attempts = (otp.getVerificationAttempts() == null ? 0 : otp.getVerificationAttempts()) + 1;
            otp.setVerificationAttempts(attempts);
            if (attempts >= MAX_ATTEMPTS) {
                otp.setUsed(true);
                otp.setUsedAt(LocalDateTime.now());
            }
            otpRepository.save(otp);
            throw new IllegalArgumentException("Invalid verification code");
        }

        otp.setVerified(true);
        otpRepository.save(otp);
        return Map.of("message", "Verification code verified successfully", "maskedEmail", maskEmail(otp.getEmail()));
    }

    @Transactional
    public Map<String, Object> resetPassword(Map<String, String> request) {
        User user = findActiveUser(request.getOrDefault("emailOrUsername", ""))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));
        PasswordChangeOtp otp = latestOtp(user.getUserId());
        validateActiveOtp(otp);

        String code = cleanOtp(request.getOrDefault("otp", ""));
        if (!Boolean.TRUE.equals(otp.getVerified()) || !otp.getOtpCode().equals(code)) {
            throw new IllegalArgumentException("Please verify the code before resetting password");
        }

        String newPassword = request.getOrDefault("newPassword", "");
        String confirmPassword = request.getOrDefault("confirmPassword", "");
        validatePassword(newPassword, confirmPassword, user);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        otp.setUsed(true);
        otp.setUsedAt(LocalDateTime.now());
        userRepository.save(user);
        otpRepository.save(otp);
        return Map.of("message", "Password reset successfully");
    }

    private Optional<User> findActiveUser(String emailOrUsername) {
        String value = emailOrUsername == null ? "" : emailOrUsername.trim();
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        Optional<User> user = userRepository.findByUsername(value);
        if (user.isEmpty()) {
            user = userRepository.findByEmail(value);
        }
        return user.filter(u -> Boolean.TRUE.equals(u.getIsActive()));
    }

    private PasswordChangeOtp latestOtp(Long userId) {
        return otpRepository.findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(userId, PURPOSE)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired verification code"));
    }

    private void validateActiveOtp(PasswordChangeOtp otp) {
        if (Boolean.TRUE.equals(otp.getUsed()) || otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true);
            otp.setUsedAt(LocalDateTime.now());
            otpRepository.save(otp);
            throw new IllegalArgumentException("Verification code has expired. Please request a new code.");
        }
        if (otp.getVerificationAttempts() != null && otp.getVerificationAttempts() >= MAX_ATTEMPTS) {
            throw new IllegalArgumentException("Too many invalid attempts. Please request a new code.");
        }
    }

    private void validatePassword(String newPassword, String confirmPassword, User user) {
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")) {
            throw new IllegalArgumentException("Password must be at least 8 characters and include uppercase, lowercase, number and special character");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
    }

    private String cleanOtp(String otp) {
        String code = otp == null ? "" : otp.trim();
        if (!code.matches("\\d{6}")) {
            throw new IllegalArgumentException("A valid 6-digit verification code is required");
        }
        return code;
    }

    private void invalidateOldOtps(Long userId) {
        List<PasswordChangeOtp> activeOtps = otpRepository.findByUserIdAndPurposeAndUsedFalse(userId, PURPOSE);
        for (PasswordChangeOtp activeOtp : activeOtps) {
            activeOtp.setUsed(true);
            activeOtp.setUsedAt(LocalDateTime.now());
        }
        otpRepository.saveAll(activeOtps);
    }

    private String generateOtp() {
        return String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
    }

    private void sendOtpEmail(User user, PasswordChangeOtp otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            String sender = resolveSenderEmail();
            log.info("Sending forgot password OTP email: enabled={}, sender={}, receiver={}, type=FORGOT_PASSWORD_OTP",
                    emailEnabled, sender, maskEmail(otp.getEmail()));
            helper.setFrom(sender);
            helper.setTo(otp.getEmail());
            helper.setSubject("TradeMaster Password Reset Verification Code");
            helper.setText(buildOtpHtml(user, otp), true);
            mailSender.send(message);
            log.info("Forgot password OTP email sent successfully to {}", maskEmail(otp.getEmail()));
        } catch (MailAuthenticationException ex) {
            markOtpFailed(otp);
            log.error("Forgot password OTP email authentication failed for {}", maskEmail(otp.getEmail()), ex);
            throw new ApiResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email authentication failed. Please verify the Gmail App Password configured in TRADEMASTER_MAIL_PASSWORD.",
                    ex
            );
        } catch (MailSendException ex) {
            markOtpFailed(otp);
            log.error("Forgot password OTP email send failed for {}", maskEmail(otp.getEmail()), ex);
            throw new ApiResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email service could not deliver the verification code. Please check SMTP configuration and Gmail security settings.",
                    ex
            );
        } catch (MailException ex) {
            markOtpFailed(otp);
            log.error("Forgot password OTP email failed for {}", maskEmail(otp.getEmail()), ex);
            throw new ApiResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email service is currently unavailable. Please try again later.",
                    ex
            );
        } catch (Exception ex) {
            markOtpFailed(otp);
            log.error("Unexpected forgot password OTP email failure for {}", maskEmail(otp.getEmail()), ex);
            throw new ApiResponseException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not send verification code because of an internal email error.",
                    ex
            );
        }
    }

    private void markOtpFailed(PasswordChangeOtp otp) {
        otp.setUsed(true);
        otp.setUsedAt(LocalDateTime.now());
        otpRepository.save(otp);
    }

    private String buildOtpHtml(User user, PasswordChangeOtp otp) {
        String displayName = StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
        String expiresText = otp.getExpiresAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH));
        return """
                <!doctype html>
                <html><body style="margin:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f7fb;padding:24px 0;"><tr><td align="center">
                <table width="620" cellpadding="0" cellspacing="0" style="max-width:620px;width:100%%;background:#fff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;">
                <tr><td style="background:#064e3b;color:#fff;padding:22px 26px;"><div style="font-size:24px;font-weight:800;">TradeMaster</div><div style="font-size:13px;opacity:.9;margin-top:4px;">Inventory and Stock Management System</div></td></tr>
                <tr><td style="padding:28px 26px;"><h2 style="margin:0 0 10px;font-size:21px;color:#111827;">Password Reset Verification</h2>
                <p style="margin:0 0 18px;font-size:14px;line-height:1.6;">Hello %s, use this 6-digit code to reset your TradeMaster password.</p>
                <div style="background:#ecfdf5;border:1px solid #bbf7d0;border-radius:10px;text-align:center;padding:22px;margin:18px 0;">
                <div style="font-size:12px;font-weight:700;color:#047857;text-transform:uppercase;">Verification Code</div>
                <div style="font-size:34px;font-weight:900;letter-spacing:8px;color:#065f46;margin-top:8px;">%s</div></div>
                <p style="margin:0 0 14px;font-size:14px;">This code expires at <strong>%s</strong>.</p>
                <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:14px;color:#9a3412;font-size:13px;">If you did not request this reset, ignore this email or contact an administrator.</div>
                </td></tr><tr><td style="background:#f8fafc;border-top:1px solid #e5e7eb;padding:16px 26px;color:#64748b;font-size:12px;">This is an auto-generated email from TradeMaster.</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(escapeHtml(displayName), otp.getOtpCode(), expiresText);
    }

    private String requireEmail(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("No email address is configured for this account. Please contact administrator.");
        }
        return user.getEmail().trim();
    }

    private void validateMailConfiguration() {
        if (!emailEnabled) {
            throw new IllegalArgumentException("Email sending is disabled. Please contact administrator.");
        }
        resolveSenderEmail();
    }

    private String resolveSenderEmail() {
        if (StringUtils.hasText(fromEmail)) return fromEmail.trim();
        if (StringUtils.hasText(mailUsername)) return mailUsername.trim();
        throw new IllegalArgumentException("Sender email is not configured");
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) return "";
        String[] parts = email.split("@", 2);
        String name = parts[0];
        String prefix = name.length() <= 3 ? name.substring(0, 1) : name.substring(0, 3);
        return prefix + "*****@" + parts[1];
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
