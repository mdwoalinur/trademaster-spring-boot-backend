package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.security.JwtUtils;
import com.trademaster.ims.security.UserDetailsImpl;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.service.AuditLogService;
import com.trademaster.ims.service.ForgotPasswordService;
import com.trademaster.ims.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "JWT login, current user profile and password recovery")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

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
    @SecurityRequirements
    @Operation(summary = "Log in and obtain a JWT",
            description = "Returns a token and the user profile. Use the token in Swagger UI's Authorize dialog.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{\"username\":\"your-username\",\"password\":\"your-password\"}"))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "JWT token and user profile"),
                    @ApiResponse(responseCode = "401", description = "Invalid credentials or inactive user"),
                    @ApiResponse(responseCode = "503", description = "Authentication service unavailable")
            })
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
            if (ex instanceof AuthenticationServiceException) {
                logger.error("Authentication service failed during login", ex);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Authentication service is unavailable. Please try again later."));
            }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid username or password"));
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
    @Operation(summary = "Get the authenticated user's profile")
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
    @SecurityRequirements
    @Operation(summary = "Request a password reset code",
            description = "Sends a verification code to the account email when an active account is found.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{\"emailOrUsername\":\"your-username\"}"))))
    public ResponseEntity<?> requestForgotPasswordOtp(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.requestOtp(request));
    }

    @PostMapping("/forgot-password/resend-otp")
    @SecurityRequirements
    @Operation(summary = "Resend a password reset code",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{\"emailOrUsername\":\"your-username\"}"))))
    public ResponseEntity<?> resendForgotPasswordOtp(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.resendOtp(request));
    }

    @PostMapping("/forgot-password/verify-otp")
    @SecurityRequirements
    @Operation(summary = "Verify a password reset code",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{\"emailOrUsername\":\"your-username\",\"otp\":\"123456\"}"))))
    public ResponseEntity<?> verifyForgotPasswordOtp(@RequestBody Map<String, String> request) {
        return handleForgotPassword(() -> forgotPasswordService.verifyOtp(request));
    }

    @PostMapping("/forgot-password/reset")
    @SecurityRequirements
    @Operation(summary = "Reset a password using a verified code",
            description = "Verify the code first, then submit the same code with matching new passwords.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            value = "{\"emailOrUsername\":\"your-username\",\"otp\":\"123456\","
                                    + "\"newPassword\":\"your-new-password\",\"confirmPassword\":\"your-new-password\"}"))))
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
