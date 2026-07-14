package com.trademaster.ims.controller;

import com.trademaster.ims.model.Notification;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "http://localhost:4200")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuthContextService authContextService;

    @PostMapping("/generate")
    public ResponseEntity<Void> generateNotifications() {
        notificationService.generateNotifications(authContextService.getCurrentCompanyId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread")
    public ResponseEntity<List<Map<String, Object>>> getUnreadNotifications() {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(authContextService.getCurrentCompanyId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount() {
        return ResponseEntity.ok(notificationService.getUnreadCount(authContextService.getCurrentCompanyId()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsReadLegacy(@PathVariable Long id) {
        return markAsRead(id);
    }

    @PostMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead(authContextService.getCurrentCompanyId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsReadLegacy() {
        return markAllAsRead();
    }

    @GetMapping
    public ResponseEntity<Page<Map<String, Object>>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean read) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(notificationService.getAllNotifications(
                authContextService.getCurrentCompanyId(), type, search, read, pageable));
    }

    @GetMapping("/all")
    public ResponseEntity<Page<Map<String, Object>>> getAllNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean read,
            @RequestParam(defaultValue = "createdAt,desc") String[] sort) {
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(sort[0]).descending());
        Page<Map<String, Object>> notifications = notificationService.getAllNotifications(
                authContextService.getCurrentCompanyId(), type, search, read, pageable);
        return ResponseEntity.ok(notifications);
    }

    @DeleteMapping("/clear-all")
    public ResponseEntity<Map<String, Object>> clearAllNotifications() {
        try {
            long deletedCount = notificationService.clearAllNotifications(
                    authContextService.getCurrentCompanyId(),
                    authContextService.getCurrentUserId()
            );
            return ResponseEntity.ok(Map.of(
                    "message", "All notifications cleared successfully",
                    "deletedCount", deletedCount
            ));
        } catch (SecurityException ex) {
            return ResponseEntity.status(403).body(Map.of(
                    "message", ex.getMessage(),
                    "deletedCount", 0
            ));
        }
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<Map<String, Object>> deleteNotifications(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request == null ? null : request.get("ids");
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message", "Notification ids are required",
                    "deletedCount", 0
            ));
        }

        int deletedCount = notificationService.deleteNotifications(authContextService.getCurrentCompanyId(), ids);
        return ResponseEntity.ok(Map.of(
                "message", "Selected notifications deleted successfully",
                "deletedCount", deletedCount
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(authContextService.getCurrentCompanyId(), id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/recent")
    public ResponseEntity<List<Map<String, Object>>> getRecentNotifications(
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(notificationService.getRecentNotifications(authContextService.getCurrentCompanyId(), limit));
    }
}
