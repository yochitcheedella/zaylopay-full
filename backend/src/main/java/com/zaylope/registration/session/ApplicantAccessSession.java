package com.zaylope.registration.session;

import com.zaylope.registration.applicant.Applicant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "applicant_access_sessions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_applicant_access_sessions_token_hash",
				columnNames = "token_hash"),
		indexes = {
				@Index(name = "ix_applicant_access_sessions_applicant", columnList = "applicant_id"),
				@Index(name = "ix_applicant_access_sessions_expires", columnList = "expires_at"),
				@Index(name = "ix_applicant_access_sessions_scope", columnList = "applicant_id, session_scope")
		})
public class ApplicantAccessSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "session_id", nullable = false, updatable = false)
	private Long sessionId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
			name = "applicant_id",
			nullable = false,
			foreignKey = @ForeignKey(name = "fk_applicant_access_sessions_applicant"))
	private Applicant applicant;

	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "session_scope", nullable = false, length = 24)
	private ApplicantSessionScope sessionScope = ApplicantSessionScope.ONBOARDING;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant expiresAt;

	@Column(name = "revoked_at", columnDefinition = "TIMESTAMP(6)")
	private Instant revokedAt;

	protected ApplicantAccessSession() {
	}

	public ApplicantAccessSession(
			Applicant applicant,
			String tokenHash,
			Instant createdAt,
			Instant expiresAt) {
		this(applicant, tokenHash, ApplicantSessionScope.ONBOARDING, createdAt, expiresAt);
	}

	public ApplicantAccessSession(
			Applicant applicant,
			String tokenHash,
			ApplicantSessionScope sessionScope,
			Instant createdAt,
			Instant expiresAt) {
		this.applicant = applicant;
		this.tokenHash = tokenHash;
		this.sessionScope = sessionScope != null ? sessionScope : ApplicantSessionScope.ONBOARDING;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public ApplicantSessionScope getSessionScope() {
		return sessionScope;
	}

	public void revoke(Instant revokedAt) {
		this.revokedAt = revokedAt;
	}
}
