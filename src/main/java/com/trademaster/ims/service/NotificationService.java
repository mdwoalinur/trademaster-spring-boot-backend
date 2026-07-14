package com.trademaster.ims.service;

import com.trademaster.ims.model.Notification;
import com.trademaster.ims.model.Product;
import com.trademaster.ims.model.Role;
import com.trademaster.ims.model.User;
import com.trademaster.ims.repository.NotificationRepository;
import com.trademaster.ims.repository.ProductRepository;
import com.trademaster.ims.repository.RoleRepository;
import com.trademaster.ims.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private final TransactionTemplate notificationTransactionTemplate;

    public NotificationService(PlatformTransactionManager transactionManager) {
        this.notificationTransactionTemplate = new TransactionTemplate(transactionManager);
        this.notificationTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public void generateNotifications(Long companyId) {
        log.info("Manual notification generation skipped. Notifications are now created from real business events.");
    }

    public List<Map<String, Object>> getUnreadNotifications(Long companyId) {
        return notificationRepository.findByCompanyIdAndIsReadFalseOrderByCreatedAtDesc(companyId)
                .stream().map(this::toDto).toList();
    }

    public long getUnreadCount(Long companyId) {
        return notificationRepository.countByCompanyIdAndIsReadFalse(companyId);
    }

    @Transactional
    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setIsRead(true);
            n.setReadAt(LocalDateTime.now());
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllAsRead(Long companyId) {
        notificationRepository.markAllAsRead(companyId);
    }

    public Page<Map<String, Object>> getAllNotifications(Long companyId, String type, String search, Boolean read, Pageable pageable) {
        boolean hasType = type != null && !type.isBlank();
        boolean hasSearch = search != null && !search.isBlank();
        Notification.NotificationType parsedType = hasType ? Notification.NotificationType.valueOf(type) : null;
        Page<Notification> page;

        if (hasType && read != null && hasSearch) {
            page = notificationRepository.searchByTypeAndReadAndTitleOrMessage(companyId, parsedType, read, search, pageable);
        } else if (hasType && read != null) {
            page = notificationRepository.findByCompanyIdAndTypeAndIsRead(companyId, parsedType, read, pageable);
        } else if (hasType && hasSearch) {
            page = notificationRepository.searchByTypeAndTitleOrMessage(companyId, parsedType, search, pageable);
        } else if (read != null && hasSearch) {
            page = notificationRepository.searchByReadAndTitleOrMessage(companyId, read, search, pageable);
        } else if (hasType) {
            page = notificationRepository.findByCompanyIdAndType(companyId, parsedType, pageable);
        } else if (read != null) {
            page = notificationRepository.findByCompanyIdAndIsRead(companyId, read, pageable);
        } else if (hasSearch) {
            page = notificationRepository.searchByTitleOrMessage(companyId, search, pageable);
        } else {
            page = notificationRepository.findByCompanyId(companyId, pageable);
        }

        return page.map(this::toDto);
    }

    @Transactional
    public void deleteNotification(Long companyId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Notification id is required");
        }
        List<Notification> notifications = notificationRepository.findByCompanyIdAndNotificationIdIn(companyId, List.of(id));
        if (!notifications.isEmpty()) {
            notificationRepository.delete(notifications.get(0));
        }
    }

    @Transactional
    public int deleteNotifications(Long companyId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Notification ids are required");
        }
        List<Long> cleanIds = ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (cleanIds.isEmpty()) {
            throw new IllegalArgumentException("Notification ids are required");
        }

        List<Notification> notifications = notificationRepository.findByCompanyIdAndNotificationIdIn(companyId, cleanIds);
        notificationRepository.deleteAll(notifications);
        return notifications.size();
    }

    @Transactional
    public long clearAllNotifications(Long companyId, Long currentUserId) {
        assertAdminUser(currentUserId);
        long deletedCount = notificationRepository.countByCompanyId(companyId);
        notificationRepository.deleteByCompanyId(companyId);
        return deletedCount;
    }

    public List<Map<String, Object>> getRecentNotifications(Long companyId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
        return notificationRepository.findByCompanyId(companyId, pageable).getContent()
                .stream().map(this::toDto).toList();
    }

    public void createSaleNotification(Long companyId, Long userId, Long saleId, String invoiceNo) {
        createOnce(companyId, userId, Notification.NotificationType.SALE, Notification.NotificationPriority.NORMAL,
                "Sale Completed",
                "Invoice " + safe(invoiceNo, "#" + saleId) + " has been completed.",
                "SALE", saleId, "/sales/edit/" + saleId);
    }

    public void createPaymentDueNotification(Long companyId, Long userId, Long saleId, String invoiceNo, Object dueAmount) {
        createOnce(companyId, userId, Notification.NotificationType.PAYMENT, Notification.NotificationPriority.MEDIUM,
                "Payment Due",
                "Invoice " + safe(invoiceNo, "#" + saleId) + " has due amount " + dueAmount + ".",
                "SALE", saleId, "/sales/edit/" + saleId);
    }

    public void createPurchaseReceivedNotification(Long companyId, Long userId, Long purchaseId, String purchaseNo) {
        createOnce(companyId, userId, Notification.NotificationType.PURCHASE, Notification.NotificationPriority.NORMAL,
                "Purchase Received",
                "Purchase order " + safe(purchaseNo, "#" + purchaseId) + " has been received into stock.",
                "PURCHASE", purchaseId, "/purchases/view/" + purchaseId);
    }

    public void createPurchaseReturnConfirmedNotification(Long companyId, Long userId, Long returnId, String returnNo) {
        createOnce(companyId, userId, Notification.NotificationType.PURCHASE_RETURN, Notification.NotificationPriority.MEDIUM,
                "Purchase Return Confirmed",
                "Purchase return " + safe(returnNo, "#" + returnId) + " has been confirmed and stock adjusted.",
                "PURCHASE_RETURN", returnId, "/purchase-returns/view/" + returnId);
    }

    public void createWastageNotification(Long companyId, Long userId, Long wastageId, String title, String message) {
        createOnce(companyId, userId, Notification.NotificationType.WASTAGE, Notification.NotificationPriority.MEDIUM,
                title, message, "WASTAGE", wastageId, "/inventory/wastage/records/edit/" + wastageId);
    }

    public void createLowStockNotification(Long companyId, Long userId, Long productId, Long warehouseId, Integer currentQty) {
        try {
            Product product = productRepository.findById(productId).orElse(null);
            if (product == null || product.getReorderLevel() == null || currentQty == null || currentQty > product.getReorderLevel()) {
                return;
            }
            String referenceType = "LOW_STOCK:" + warehouseId;
            runNotificationTransaction("Low Stock Alert", () -> {
                if (notificationRepository.findFirstByCompanyIdAndTypeAndReferenceTypeAndReferenceIdAndIsReadFalse(
                        companyId, Notification.NotificationType.LOW_STOCK, referenceType, productId).isPresent()) {
                    return;
                }
                create(companyId, userId, Notification.NotificationType.LOW_STOCK, Notification.NotificationPriority.HIGH,
                        "Low Stock Alert",
                        product.getProductName() + " has only " + currentQty + " units left. Reorder level: " + product.getReorderLevel() + ".",
                        referenceType, productId, "/inventory/low-stock");
            });
        } catch (Exception ex) {
            log.error("Failed to create low stock notification", ex);
        }
    }

    private void createOnce(Long companyId, Long userId, Notification.NotificationType type,
                            Notification.NotificationPriority priority, String title, String message,
                            String referenceType, Long referenceId, String route) {
        runNotificationTransaction(title, () -> {
            if (referenceId != null && notificationRepository.existsByCompanyIdAndTypeAndReferenceTypeAndReferenceId(companyId, type, referenceType, referenceId)) {
                return;
            }
            create(companyId, userId, type, priority, title, message, referenceType, referenceId, route);
        });
    }

    private void create(Long companyId, Long userId, Notification.NotificationType type,
                        Notification.NotificationPriority priority, String title, String message,
                        String referenceType, Long referenceId, String route) {
        Notification n = new Notification();
        n.setCompanyId(companyId);
        n.setUserId(userId);
        n.setCreatedBy(userId);
        n.setType(type);
        n.setPriority(priority);
        n.setTitle(title);
        n.setMessage(message);
        n.setReferenceType(referenceType);
        n.setReferenceId(referenceId);
        n.setActionUrl(route);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.saveAndFlush(n);
    }

    private void runNotificationTransaction(String title, Runnable action) {
        try {
            notificationTransactionTemplate.executeWithoutResult(status -> action.run());
        } catch (Exception ex) {
            log.error("Failed to create notification: {}", title, ex);
        }
    }

    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", n.getNotificationId());
        dto.put("notificationId", n.getNotificationId());
        dto.put("title", n.getTitle());
        dto.put("message", n.getMessage());
        dto.put("type", n.getType());
        dto.put("priority", n.getPriority());
        dto.put("read", Boolean.TRUE.equals(n.getIsRead()));
        dto.put("isRead", Boolean.TRUE.equals(n.getIsRead()));
        dto.put("referenceType", n.getReferenceType());
        dto.put("referenceId", n.getReferenceId());
        dto.put("route", n.getActionUrl());
        dto.put("actionUrl", n.getActionUrl());
        dto.put("createdAt", n.getCreatedAt());
        dto.put("readAt", n.getReadAt());
        dto.put("timeAgo", timeAgo(n.getCreatedAt()));
        dto.put("icon", icon(n.getType()));
        dto.put("color", color(n.getType(), n.getPriority()));
        return dto;
    }

    private String icon(Notification.NotificationType type) {
        if (type == null) return "bi-bell";
        return switch (type) {
            case LOW_STOCK -> "bi-exclamation-triangle";
            case SALE -> "bi-receipt";
            case PURCHASE -> "bi-truck";
            case PURCHASE_RETURN -> "bi-arrow-counterclockwise";
            case PAYMENT, DUE_PAYMENT -> "bi-cash-stack";
            case WASTAGE -> "bi-trash3";
            case SYSTEM -> "bi-gear";
            default -> "bi-bell";
        };
    }

    private String color(Notification.NotificationType type, Notification.NotificationPriority priority) {
        if (priority == Notification.NotificationPriority.HIGH) return "danger";
        if (type == null) return "secondary";
        return switch (type) {
            case LOW_STOCK, WASTAGE -> "warning";
            case SALE -> "success";
            case PURCHASE -> "primary";
            case PURCHASE_RETURN -> "orange";
            case PAYMENT, DUE_PAYMENT -> "purple";
            case SYSTEM -> "info";
            default -> "secondary";
        };
    }

    private String timeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return "";
        Duration duration = Duration.between(createdAt, LocalDateTime.now());
        if (duration.toMinutes() < 1) return "Just now";
        if (duration.toHours() < 1) return duration.toMinutes() + " min ago";
        if (duration.toDays() < 1) return duration.toHours() + " hr ago";
        return duration.toDays() + " day" + (duration.toDays() > 1 ? "s" : "") + " ago";
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void assertAdminUser(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user was not found"));
        String roleName = roleRepository.findById(user.getRoleId())
                .map(Role::getRoleName)
                .orElse("");
        String normalizedRole = roleName.toUpperCase(Locale.ROOT);
        if (!"ADMIN".equals(normalizedRole) && !"SUPER_ADMIN".equals(normalizedRole)) {
            throw new SecurityException("Only ADMIN or SUPER_ADMIN can clear notifications");
        }
    }
}
