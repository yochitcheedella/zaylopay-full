package com.zaylope.registration.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "applicant_document_audit_events")
public class ApplicantDocumentAuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "audit_event_id", nullable = false, updatable = false)
	private Long auditEventId;

	@Column(name = "document_id", length = 36, nullable = false, updatable = false)
	private String documentId;

	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "actor_role", length = 32, nullable = false, updatable = false)
	private String actorRole;

	@Column(name = "action", length = 32, nullable = false, updatable = false)
	private String action;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	protected ApplicantDocumentAuditEvent() {
	}

	public ApplicantDocumentAuditEvent(
			String documentId,
			Long applicantId,
			String actorRole,
			String action,
			Instant occurredAt) {
		this.documentId = documentId;
		this.applicantId = applicantId;
		this.actorRole = actorRole;
		this.action = action;
		this.occurredAt = occurredAt;
	}

	public Long getAuditEventId() {
		return auditEventId;
	}

	public String getDocumentId() {
		return documentId;
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public String getActorRole() {
		return actorRole;
	}

	public String getAction() {
		return action;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}
}
