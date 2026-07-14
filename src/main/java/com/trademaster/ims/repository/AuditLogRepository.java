package com.trademaster.ims.repository;

import com.trademaster.ims.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByUserId(Long userId, Pageable pageable);

    List<AuditLog> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    Page<AuditLog> findByAction(String action, Pageable pageable);

    @Query("SELECT l FROM AuditLog l WHERE " +
           "(:userId IS NULL OR l.userId = :userId) AND " +
           "(:action IS NULL OR l.action = :action) AND " +
           "(:entityType IS NULL OR l.entityType = :entityType) AND " +
           "(:search IS NULL OR " +
           "LOWER(l.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.action) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.entityType) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.ipAddress) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.userAgent) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.oldValue) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(l.newValue) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AuditLog> search(@Param("userId") Long userId,
                          @Param("action") String action,
                          @Param("entityType") String entityType,
                          @Param("search") String search,
                          Pageable pageable);
}
