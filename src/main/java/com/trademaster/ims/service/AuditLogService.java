package com.trademaster.ims.service;

import com.trademaster.ims.model.AuditLog;
import com.trademaster.ims.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    public void saveLog(Long userId, String username, String action, String entityType, Long entityId,
                        String oldValue, String newValue, HttpServletRequest request) {
        try {
            Long resolvedUserId = userId != null ? userId : resolveLoggedInUserIdOrZero();
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(resolvedUserId);
            auditLog.setUsername(username != null && !username.isBlank() ? username : "system");
            auditLog.setAction(action);
            auditLog.setEntityType(entityType);
            auditLog.setEntityId(entityId != null ? entityId : resolvedUserId);
            auditLog.setOldValue(oldValue);
            auditLog.setNewValue(newValue);
            if (request != null) {
                auditLog.setIpAddress(getClientIp(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            auditLogRepository.saveAndFlush(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log, but business operation will continue", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    public Page<AuditLog> getLogs(Long userId, String action, String entityType, String search, Pageable pageable) {
        if (userId != null || action != null || entityType != null || (search != null && !search.isEmpty())) {
            return auditLogRepository.search(userId, action, entityType, search, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    public AuditLog getLogById(Long id) {
        return auditLogRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Audit log not found"));
    }

    private Long resolveLoggedInUserIdOrZero() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof com.trademaster.ims.security.UserDetailsImpl) {
                return ((com.trademaster.ims.security.UserDetailsImpl) principal).getUserId();
            }
        } catch (Exception e) {
            log.debug("Could not resolve logged-in user for audit fallback", e);
        }
        return 0L;
    }
}
