package com.trademaster.ims.repository;

import com.trademaster.ims.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // ==================== Unread Notifications ====================
    List<Notification> findByCompanyIdAndIsReadFalseOrderByCreatedAtDesc(Long companyId);

    long countByCompanyIdAndIsReadFalse(Long companyId);

    long countByCompanyId(Long companyId);

    // ==================== Mark All as Read ====================
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.companyId = :companyId AND n.isRead = false")
    void markAllAsRead(@Param("companyId") Long companyId);

    // ==================== Paginated Queries ====================
    Page<Notification> findByCompanyId(Long companyId, Pageable pageable);

    Page<Notification> findByCompanyIdAndType(Long companyId, Notification.NotificationType type, Pageable pageable);

    Page<Notification> findByCompanyIdAndIsRead(Long companyId, Boolean isRead, Pageable pageable);

    Page<Notification> findByCompanyIdAndTypeAndIsRead(Long companyId, Notification.NotificationType type, Boolean isRead, Pageable pageable);

    List<Notification> findByCompanyIdAndNotificationIdIn(Long companyId, List<Long> notificationIds);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.companyId = :companyId")
    int deleteByCompanyId(@Param("companyId") Long companyId);

    @Query("SELECT n FROM Notification n WHERE n.companyId = :companyId AND " +
            "(LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Notification> searchByTitleOrMessage(
            @Param("companyId") Long companyId,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.companyId = :companyId AND n.type = :type AND " +
            "(LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Notification> searchByTypeAndTitleOrMessage(
            @Param("companyId") Long companyId,
            @Param("type") Notification.NotificationType type,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.companyId = :companyId AND n.isRead = :isRead AND " +
            "(LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Notification> searchByReadAndTitleOrMessage(
            @Param("companyId") Long companyId,
            @Param("isRead") Boolean isRead,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.companyId = :companyId AND n.type = :type AND n.isRead = :isRead AND " +
            "(LOWER(n.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(n.message) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Notification> searchByTypeAndReadAndTitleOrMessage(
            @Param("companyId") Long companyId,
            @Param("type") Notification.NotificationType type,
            @Param("isRead") Boolean isRead,
            @Param("search") String search,
            Pageable pageable);

    // ==================== Additional Useful Queries ====================
    List<Notification> findByCompanyIdAndTypeOrderByCreatedAtDesc(Long companyId, Notification.NotificationType type);

    Optional<Notification> findFirstByCompanyIdAndTypeAndReferenceTypeAndReferenceIdAndIsReadFalse(
            Long companyId, Notification.NotificationType type, String referenceType, Long referenceId);

    boolean existsByCompanyIdAndTypeAndReferenceTypeAndReferenceId(
            Long companyId, Notification.NotificationType type, String referenceType, Long referenceId);

    void deleteByCompanyIdAndIsReadTrue(Long companyId); // optional: cleanup old read notifications
}
