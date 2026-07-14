package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.security.JwtUtils;
import com.trademaster.ims.security.UserDetailsImpl;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.service.AuditLogService;
import com.trademaster.ims.service.ForgotPasswordService;
import com.trademaster.ims.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:4200")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ForgotPasswordService forgotPasswordService;

    @Autowired
    private AuditLogService auditLogService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody Map<String, String> loginRequest, HttpServletRequest request) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password));
        } catch (AuthenticationException ex) {
            auditLogService.saveLog(null, username, "LOGIN_FAILED", "Auth", 0L, null,
                    "{\"success\":false,\"message\":\"Login failed\"}", request);
            throw ex;
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Long userId = ((UserDetailsImpl) userDetails).getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.putAll(buildUserProfile(user));
        auditLogService.saveLog(userId, user.getUsername(), "LOGIN_SUCCESS", "Auth", userId, null,
                "{\"success\":true,\"message\":\"Login successful\"}", request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof UserDetailsImpl)) {
            return ResponseEntity.status(401).body("Authenticated user context is required");
        }

        Long userId = ((UserDetailsImpl) principal).getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return ResponseEntity.ok(profileService.buildSafeProfile(user));
    }

    @PostMapping("/forgot-password/request-otp")
    public ResponseEntity<?> requestForgotPasswordOtp(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.requestOtp(request));
    }

    @PostMapping("/forgot-password/resend-otp")
    public ResponseEntity<?> resendForgotPasswordOtp(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.resendOtp(request));
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<?> verifyForgotPasswordOtp(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.verifyOtp(request));
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetForgotPassword(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.resetPassword(request));
    }

    private Map<String, Object> buildUserProfile(User user) {
        return profileService.buildSafeProfile(user);
    }

    private ResponseEntity<?> handleForgotPassword(ForgotPasswordAction action) {
        try {
            return ResponseEntity.ok(action.execute());
        } catch (ApiResponseException ex) {
            return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Password reset request failed. Please try again later."));
        }
    }

    @FunctionalInterface
    private interface ForgotPasswordAction {
        Map<String, Object> execute();
    }
}
