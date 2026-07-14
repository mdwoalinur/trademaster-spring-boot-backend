package com.trademaster.ims.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthContextService {

    @Value("${app.default-company-id:1}")
    private Long defaultCompanyId;

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetailsImpl) {
            return ((UserDetailsImpl) authentication.getPrincipal()).getUserId();
        }
        throw new IllegalStateException("Authenticated user context is required");
    }

    public Long getCurrentCompanyId() {
        return defaultCompanyId;
    }
}
