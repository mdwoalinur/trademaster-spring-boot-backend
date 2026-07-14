package com.trademaster.ims.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MailDiagnosticsService {

    private final JavaMailSender mailSender;
    private final ConfigurableEnvironment environment;

    @Value("${spring.mail.host:}")
    private String host;

    @Value("${spring.mail.port:0}")
    private int port;

    @Value("${spring.mail.username:}")
    private String username;

    @Value("${spring.mail.password:}")
    private String password;

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${app.email.from:}")
    private String fromEmail;

    public MailDiagnosticsService(JavaMailSender mailSender, ConfigurableEnvironment environment) {
        this.mailSender = mailSender;
        this.environment = environment;
    }

    public Map<String, Object> safeDiagnostics() {
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("emailEnabled", emailEnabled);
        diagnostics.put("smtpHost", host);
        diagnostics.put("smtpPort", port);
        diagnostics.put("username", username);
        diagnostics.put("senderEmail", fromEmail);
        diagnostics.put("passwordPresent", StringUtils.hasText(password));
        diagnostics.put("passwordLength", password == null ? 0 : password.length());
        diagnostics.put("passwordHasLeadingOrTrailingWhitespace", password != null && !password.equals(password.trim()));
        diagnostics.put("passwordContainsWhitespace", password != null && password.matches(".*\\s+.*"));
        diagnostics.put("mailSenderClass", mailSender.getClass().getName());
        diagnostics.put("javaMailSenderImpl", mailSender instanceof JavaMailSenderImpl);
        diagnostics.put("activeProfiles", environment.getActiveProfiles());
        diagnostics.put("propertySources", relevantPropertySources());
        return diagnostics;
    }

    public Map<String, Object> testConnection() {
        Map<String, Object> result = new LinkedHashMap<>(safeDiagnostics());
        if (!(mailSender instanceof JavaMailSenderImpl javaMailSender)) {
            result.put("status", "SKIPPED");
            result.put("message", "JavaMailSender is not JavaMailSenderImpl, so testConnection is not available.");
            return result;
        }

        try {
            javaMailSender.testConnection();
            result.put("status", "OK");
            result.put("message", "SMTP connection and authentication succeeded.");
            return result;
        } catch (Exception ex) {
            result.put("status", "FAILED");
            result.put("exceptionClass", ex.getClass().getName());
            result.put("rootCauseClass", rootCause(ex).getClass().getName());
            result.put("message", safeExceptionMessage(rootCause(ex)));
            return result;
        }
    }

    private List<Map<String, Object>> relevantPropertySources() {
        List<Map<String, Object>> sources = new ArrayList<>();
        String[] keys = {
                "spring.mail.host",
                "spring.mail.port",
                "spring.mail.username",
                "spring.mail.password",
                "app.email.enabled",
                "app.email.from",
                "TRADEMASTER_MAIL_USERNAME",
                "TRADEMASTER_MAIL_PASSWORD",
                "TRADEMASTER_EMAIL_ENABLED",
                "TRADEMASTER_EMAIL_FROM"
        };

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            List<String> presentKeys = new ArrayList<>();
            for (String key : keys) {
                if (propertySource.containsProperty(key)) {
                    presentKeys.add(key);
                }
            }
            if (!presentKeys.isEmpty()) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("name", propertySource.getName());
                source.put("keys", presentKeys);
                sources.add(source);
            }
        }
        return sources;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeExceptionMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return StringUtils.hasText(message) ? message.replace(password == null ? "" : password, "[REDACTED]") : "SMTP connection failed";
    }
}
