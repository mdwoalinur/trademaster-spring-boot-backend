package com.trademaster.ims.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_approval_history")
public class PaymentApprovalHistory {

    public enum ApprovalAction {
        CREATED, UPDATED, SUBMITTED, APPROVED, REJECTED, RETURNED_FOR_CORRECTION, POSTED, VOIDED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "approval_history_id")
    private Long approvalHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    @JsonIgnore
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private ApprovalAction action;

    @Column(name = "previous_status", length = 50)
    private String previousStatus;

    @Column(name = "new_status", length = 50)
    private String newStatus;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "acted_by", nullable = false)
    private Long actedBy;

    @Column(name = "acted_at", nullable = false)
    private LocalDateTime actedAt = LocalDateTime.now();

    public Long getApprovalHistoryId() { return approvalHistoryId; }
    public void setApprovalHistoryId(Long approvalHistoryId) { this.approvalHistoryId = approvalHistoryId; }
    public Payment getPayment() { return payment; }
    public void setPayment(Payment payment) { this.payment = payment; }
    public ApprovalAction getAction() { return action; }
    public void setAction(ApprovalAction action) { this.action = action; }
    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String previousStatus) { this.previousStatus = previousStatus; }
    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public Long getActedBy() { return actedBy; }
    public void setActedBy(Long actedBy) { this.actedBy = actedBy; }
    public LocalDateTime getActedAt() { return actedAt; }
    public void setActedAt(LocalDateTime actedAt) { this.actedAt = actedAt; }
}
