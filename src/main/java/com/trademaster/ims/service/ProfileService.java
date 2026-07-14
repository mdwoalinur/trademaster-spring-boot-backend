package com.trademaster.ims.service;

import com.trademaster.ims.model.AuditLog;
import com.trademaster.ims.model.PasswordChangeOtp;
import com.trademaster.ims.model.Role;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.AuditLogRepository;
import com.trademaster.ims.repository.PasswordChangeOtpRepository;
import com.trademaster.ims.repository.RoleRepository;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.util.FileStorageService;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProfileService {

    private static final long MAX_PROFILE_PHOTO_SIZE = 2L * 1024L * 1024L;
    private static final int PASSWORD_OTP_EXPIRY_MINUTES = 10;
    private static final String OTP_PURPOSE_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    private static final SecureRandom OTP_RANDOM = new SecureRandom();

    private final AuthContextService authContextService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordChangeOtpRepository passwordChangeOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;
    private final JavaMailSender mailSender;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:${spring.mail.username:}}")
    private String fromEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.email.company-name:TradeMaster}")
    private String companyName;

    public ProfileService(
            AuthContextService authContextService,
            UserRepository userRepository,
            RoleRepository roleRepository,
            AuditLogRepository auditLogRepository,
            PasswordChangeOtpRepository passwordChangeOtpRepository,
            PasswordEncoder passwordEncoder,
            FileStorageService fileStorageService,
            JavaMailSender mailSender
    ) {
        this.authContextService = authContextService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordChangeOtpRepository = passwordChangeOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
        this.mailSender = mailSender;
    }

    public Map<String, Object> getMyProfile() {
        return buildSafeProfile(getCurrentUser());
    }

    public Map<String, Object> updateMyProfile(Map<String, Object> request) {
        User user = getCurrentUser();
        user.setFullName(clean(request.get("fullName")));
        user.setPhone(clean(request.get("phone")));
        user.setAddress(clean(request.get("address")));
        userRepository.save(user);
        return buildSafeProfile(user);
    }

    public Map<String, Object> uploadPhoto(MultipartFile file) throws IOException {
        validateProfilePhoto(file);
        User user = getCurrentUser();
        String storedFileName = fileStorageService.storeFile(file, FileStorageService.PROFILES);
        user.setProfileImageUrl("/api/files/profiles/" + storedFileName);
        userRepository.save(user);
        return buildSafeProfile(user);
    }

    @Transactional
    public Map<String, Object> changePassword(Map<String, String> request) {
        return requestPasswordChangeOtp(request);
    }

    @Transactional
    public Map<String, Object> requestPasswordChangeOtp(Map<String, String> request) {
        String currentPassword = request.getOrDefault("currentPassword", "");
        String newPassword = request.getOrDefault("newPassword", "");
        String confirmPassword = request.getOrDefault("confirmPassword", "");
        User user = getCurrentUser();

        if (currentPassword.isBlank()) {
            throw new IllegalArgumentException("Current password is required");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("New password must be different from current password");
        }
        String recipientEmail = requireUserEmail(user);
        validateOtpMailConfiguration();

        invalidateOldPasswordOtps(user.getUserId());
        String otpCode = generateOtp();
        PasswordChangeOtp otp = new PasswordChangeOtp();
        otp.setUserId(user.getUserId());
        otp.setEmail(recipientEmail);
        otp.setOtpCode(otpCode);
        otp.setPurpose(OTP_PURPOSE_CHANGE_PASSWORD);
        otp.setEncodedNewPassword(passwordEncoder.encode(newPassword));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(PASSWORD_OTP_EXPIRY_MINUTES));
        otp.setLastSentAt(LocalDateTime.now());
        passwordChangeOtpRepository.save(otp);

        try {
            sendPasswordChangeOtpEmail(user, otpCode, otp.getExpiresAt());
        } catch (Exception ex) {
            otp.setUsed(true);
            otp.setUsedAt(LocalDateTime.now());
            passwordChangeOtpRepository.save(otp);
            throw new IllegalArgumentException("Could not send confirmation code to your email. Please try again later.");
        }

        return Map.of("message", "Confirmation code has been sent to your email.");
    }

    @Transactional
    public Map<String, Object> confirmPasswordChange(Map<String, String> request) {
        String otpCode = request.getOrDefault("otpCode", "").trim();
        if (!otpCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("A valid 6-digit OTP code is required");
        }

        User user = getCurrentUser();
        PasswordChangeOtp otp = passwordChangeOtpRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getUserId(), OTP_PURPOSE_CHANGE_PASSWORD)
                .orElseThrow(() -> new IllegalArgumentException("No active confirmation code found. Please request a new code."));

        if (Boolean.TRUE.equals(otp.getUsed())) {
            throw new IllegalArgumentException("This confirmation code was already used");
        }
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true);
            otp.setUsedAt(LocalDateTime.now());
            passwordChangeOtpRepository.save(otp);
            throw new IllegalArgumentException("Confirmation code has expired. Please request a new code.");
        }
        if (!otp.getOtpCode().equals(otpCode)) {
            throw new IllegalArgumentException("Invalid confirmation code");
        }

        user.setPasswordHash(otp.getEncodedNewPassword());
        otp.setUsed(true);
        otp.setUsedAt(LocalDateTime.now());
        userRepository.save(user);
        passwordChangeOtpRepository.save(otp);
        return Map.of("message", "Password changed successfully");
    }

    @Transactional
    public Map<String, Object> resendPasswordChangeOtp() {
        User user = getCurrentUser();
        PasswordChangeOtp otp = passwordChangeOtpRepository
                .findTopByUserIdAndPurposeAndUsedFalseOrderByCreatedAtDesc(user.getUserId(), OTP_PURPOSE_CHANGE_PASSWORD)
                .orElseThrow(() -> new IllegalArgumentException("No active password change request found"));
        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            otp.setUsed(true);
            otp.setUsedAt(LocalDateTime.now());
            passwordChangeOtpRepository.save(otp);
            throw new IllegalArgumentException("Confirmation code has expired. Please request a new code.");
        }
        requireUserEmail(user);
        validateOtpMailConfiguration();
        try {
            sendPasswordChangeOtpEmail(user, otp.getOtpCode(), otp.getExpiresAt());
            otp.setLastSentAt(LocalDateTime.now());
            passwordChangeOtpRepository.save(otp);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Could not resend confirmation code. Please try again later.");
        }
        return Map.of("message", "Confirmation code has been resent to your email.");
    }

    public List<AuditLog> getMyActivityLogs() {
        return auditLogRepository.findTop50ByUserIdOrderByCreatedAtDesc(authContextService.getCurrentUserId());
    }

    public Map<String, Object> updateMySettings(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Settings saved successfully");
        response.put("settings", request == null ? Map.of() : request);
        response.put("updatedAt", LocalDateTime.now());
        return response;
    }

    public Map<String, Object> buildSafeProfile(User user) {
        String roleName = roleRepository.findById(user.getRoleId())
                .map(Role::getRoleName)
                .orElse("USER");

        Map<String, Object> profile = new HashMap<>();
        profile.put("userId", user.getUserId());
        profile.put("username", user.getUsername());
        profile.put("fullName", user.getFullName());
        profile.put("email", user.getEmail());
        profile.put("phone", user.getPhone());
        profile.put("department", user.getDepartment());
        profile.put("designation", user.getDesignation());
        profile.put("address", user.getAddress());
        profile.put("profileImageUrl", normalizeProfileImageUrl(user.getProfileImageUrl()));
        profile.put("roleId", user.getRoleId());
        profile.put("roleName", roleName);
        profile.put("roleDisplayName", roleDisplayName(roleName));
        profile.put("isActive", user.getIsActive());
        profile.put("status", Boolean.TRUE.equals(user.getIsActive()) ? "Active" : "Inactive");
        profile.put("lastLogin", user.getLastLogin());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("updatedAt", user.getUpdatedAt());
        return profile;
    }

    private String normalizeProfileImageUrl(String value) {
        if (value == null || value.isBlank() || value.startsWith("/api/files/profiles/")) return value;
        if (value.startsWith("/api/files/")) {
            return "/api/files/profiles/" + value.substring("/api/files/".length());
        }
        return value;
    }

    private User getCurrentUser() {
        Long userId = authContextService.getCurrentUserId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user was not found"));
    }

    private void validateProfilePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Profile photo is required");
        }
        if (file.getSize() > MAX_PROFILE_PHOTO_SIZE) {
            throw new IllegalArgumentException("Profile photo must be 2MB or smaller");
        }

        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename();
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        boolean validType = "image/jpeg".equalsIgnoreCase(contentType) || "image/png".equalsIgnoreCase(contentType);
        boolean validExt = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png");
        if (!validType || !validExt) {
            throw new IllegalArgumentException("Only JPG, JPEG and PNG profile photos are supported");
        }
    }

    private String roleDisplayName(String roleName) {
        if (roleName == null) {
            return "User";
        }
        return switch (roleName.toUpperCase(Locale.ROOT)) {
            case "SUPER_ADMIN" -> "Super Administrator";
            case "ADMIN" -> "Administrator";
            case "MANAGER" -> "Manager";
            case "SALESMAN" -> "Sales Executive";
            case "EMPLOYEE" -> "Employee";
            default -> roleName.replace("_", " ");
        };
    }

    private void invalidateOldPasswordOtps(Long userId) {
        List<PasswordChangeOtp> activeOtps = passwordChangeOtpRepository.findByUserIdAndPurposeAndUsedFalse(userId, OTP_PURPOSE_CHANGE_PASSWORD);
        for (PasswordChangeOtp activeOtp : activeOtps) {
            activeOtp.setUsed(true);
            activeOtp.setUsedAt(LocalDateTime.now());
        }
        passwordChangeOtpRepository.saveAll(activeOtps);
    }

    private String generateOtp() {
        return String.format("%06d", OTP_RANDOM.nextInt(1_000_000));
    }

    private void sendPasswordChangeOtpEmail(User user, String otpCode, LocalDateTime expiresAt) throws Exception {
        String recipientEmail = requireUserEmail(user);
        String senderEmail = resolveSenderEmail();
        String subject = "TradeMaster Password Change Confirmation Code";

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(senderEmail);
        helper.setTo(recipientEmail);
        helper.setSubject(subject);
        helper.setText(buildPasswordOtpHtml(user, otpCode, expiresAt), true);
        mailSender.send(message);
    }

    private String buildPasswordOtpHtml(User user, String otpCode, LocalDateTime expiresAt) {
        String displayName = StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
        String expiresText = expiresAt.format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH));
        return """
                <!doctype html>
                <html>
                <body style="margin:0;background:#f4f7fb;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f7fb;padding:24px 0;">
                    <tr>
                      <td align="center">
                        <table width="620" cellpadding="0" cellspacing="0" style="max-width:620px;width:100%%;background:#ffffff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;">
                          <tr>
                            <td style="background:#0f3d63;color:#ffffff;padding:22px 26px;">
                              <div style="font-size:24px;font-weight:800;letter-spacing:.2px;">TradeMaster</div>
                              <div style="font-size:13px;opacity:.88;margin-top:4px;">Inventory and Stock Management System</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:28px 26px;">
                              <h2 style="margin:0 0 10px;font-size:21px;color:#111827;">Password Change Confirmation</h2>
                              <p style="margin:0 0 18px;font-size:14px;line-height:1.6;">Hello %s, use the confirmation code below to complete your password change request.</p>
                              <div style="background:#eef6ff;border:1px solid #bfdbfe;border-radius:8px;text-align:center;padding:22px;margin:18px 0;">
                                <div style="font-size:12px;font-weight:700;color:#475569;text-transform:uppercase;">Confirmation Code</div>
                                <div style="font-size:34px;font-weight:900;letter-spacing:8px;color:#0f3d63;margin-top:8px;">%s</div>
                              </div>
                              <p style="margin:0 0 14px;font-size:14px;">This code will expire at <strong>%s</strong>.</p>
                              <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:14px;color:#9a3412;font-size:13px;line-height:1.5;">
                                If you did not request this password change, please ignore this email or contact administrator.
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="background:#f8fafc;border-top:1px solid #e5e7eb;padding:16px 26px;color:#64748b;font-size:12px;">
                              This is an auto-generated email from TradeMaster.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(escapeHtml(displayName), otpCode, expiresText);
    }

    private String requireUserEmail(User user) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            throw new IllegalArgumentException("Your account email is missing. Please contact administrator before changing password.");
        }
        return user.getEmail().trim();
    }

    private String resolveSenderEmail() {
        if (StringUtils.hasText(fromEmail)) {
            return fromEmail.trim();
        }
        if (StringUtils.hasText(mailUsername)) {
            return mailUsername.trim();
        }
        throw new IllegalArgumentException("Sender email is not configured");
    }

    private void validateOtpMailConfiguration() {
        if (!emailEnabled) {
            throw new IllegalArgumentException("Email sending is disabled. Password confirmation code cannot be sent.");
        }
        resolveSenderEmail();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String clean(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
