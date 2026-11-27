package com.zaylope.registration.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
		name = "admin_approval_history",
		indexes = {
				@Index(name = "ix_approval_history_applicant", columnList = "applicant_id"),
				@Index(name = "ix_approval_history_occurred", columnList = "occurred_at")
		})
public class AdminApprovalHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "approval_id", nullable = false, updatable = false)
	private Long approvalId;

	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "admin_username", nullable = false, length = 64)
	private String adminUsername;

	@Column(name = "action", nullable = false, length = 32)
	private String action;

	@Column(name = "reason_or_notes", length = 500)
	private String reasonOrNotes;

	@Column(name = "occurred_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant occurredAt;

	protected AdminApprovalHistory() {
	}

	public AdminApprovalHistory(Long applicantId, String adminUsername, String action, String reasonOrNotes) {
		this.applicantId = applicantId;
		this.adminUsername = adminUsername;
		this.action = action;
		this.reasonOrNotes = reasonOrNotes;
	}

	@PrePersist
	void initializeTimestamps() {
		this.occurredAt = Instant.now();
	}

	public Long getApprovalId() {
		return approvalId;
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public String getAdminUsername() {
		return adminUsername;
	}

	public String getAction() {
		return action;
	}

	public String getReasonOrNotes() {
		return reasonOrNotes;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
