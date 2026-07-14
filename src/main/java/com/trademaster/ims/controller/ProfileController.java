package com.trademaster.ims.controller;

import com.trademaster.ims.model.AuditLog;
import com.trademaster.ims.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMyProfile() {
        return ResponseEntity.ok(profileService.getMyProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateMyProfile(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(profileService.updateMyProfile(request));
    }

    @PostMapping("/upload-photo")
    public ResponseEntity<?> uploadPhoto(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.ok(profileService.uploadPhoto(file));
        } catch (Exception ex) {
            log.error("Profile photo upload failed", ex);
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(profileService.changePassword(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/change-password/request-otp")
    public ResponseEntity<?> requestPasswordChangeOtp(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(profileService.requestPasswordChangeOtp(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/change-password/confirm")
    public ResponseEntity<?> confirmPasswordChange(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(profileService.confirmPasswordChange(request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/change-password/resend-otp")
    public ResponseEntity<?> resendPasswordChangeOtp() {
        try {
            return ResponseEntity.ok(profileService.resendPasswordChangeOtp());
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/activity-logs")
    public ResponseEntity<List<AuditLog>> getMyActivityLogs() {
        return ResponseEntity.ok(profileService.getMyActivityLogs());
    }

    @PutMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateMySettings(@RequestBody Map<String, Object> request) {
        return ResponseEntity.ok(profileService.updateMySettings(request));
    }
}
