package com.trademaster.ims.security;

import com.trademaster.ims.model.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class UserDetailsImpl implements UserDetails {
    private static final long serialVersionUID = 1L;

    private static final Set<String> EMPLOYEE_PERMISSIONS = Set.of(
            "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_EDIT_DRAFT", "PAYMENT_SUBMIT", "PAYMENT_ATTACHMENT_MANAGE"
    );
    private static final Set<String> MANAGER_PERMISSIONS = Set.of(
            "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_SUBMIT"
    );
    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
            "PAYMENT_VIEW", "PAYMENT_CREATE", "PAYMENT_EDIT_DRAFT", "PAYMENT_SUBMIT",
            "PAYMENT_APPROVE", "PAYMENT_REJECT", "PAYMENT_RETURN", "PAYMENT_POST", "PAYMENT_CANCEL",
            "PAYMENT_VOID", "PAYMENT_REFUND", "PAYMENT_RECONCILE", "PAYMENT_ACCOUNT_VIEW",
            "PAYMENT_ACCOUNT_MANAGE", "PAYMENT_LEDGER_VIEW", "PAYMENT_ATTACHMENT_MANAGE",
            "PAYMENT_REPORT_VIEW", "PAYMENT_DASHBOARD_VIEW", "AUDIT_LOG_VIEW"
    );
    private static final Set<String> SUPER_ADMIN_EXTRA_PERMISSIONS = Set.of("PAYMENT_SELF_APPROVE");

    private final Long userId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserDetailsImpl(Long userId, String username, String password, boolean enabled,
                           Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.enabled = enabled;
        this.authorities = authorities;
    }

    public static UserDetailsImpl build(User user) {
        return build(user, null);
    }

    public static UserDetailsImpl build(User user, String roleName) {
        String normalizedRole = normalizeRoleName(roleName);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));
        permissionsFor(normalizedRole).forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        return new UserDetailsImpl(user.getUserId(), user.getUsername(), user.getPasswordHash(),
                Boolean.TRUE.equals(user.getIsActive()), authorities);
    }

    private static String normalizeRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "USER";
        }
        String normalized = roleName.trim()
                .toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        return switch (normalized) {
            case "SUPERADMIN", "SUPER_ADMINISTRATOR" -> "SUPER_ADMIN";
            case "ADMINISTRATOR" -> "ADMIN";
            case "SALES_EXECUTIVE" -> "SALESMAN";
            default -> normalized;
        };
    }

    private static Set<String> permissionsFor(String roleName) {
        return switch (roleName) {
            case "SUPER_ADMIN" -> {
                java.util.HashSet<String> permissions = new java.util.HashSet<>(ADMIN_PERMISSIONS);
                permissions.addAll(SUPER_ADMIN_EXTRA_PERMISSIONS);
                yield permissions;
            }
            case "ADMIN" -> ADMIN_PERMISSIONS;
            case "MANAGER" -> MANAGER_PERMISSIONS;
            case "SALESMAN", "EMPLOYEE", "ACCOUNTANT", "USER" -> EMPLOYEE_PERMISSIONS;
            default -> EMPLOYEE_PERMISSIONS;
        };
    }

    public Long getUserId() { return userId; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
