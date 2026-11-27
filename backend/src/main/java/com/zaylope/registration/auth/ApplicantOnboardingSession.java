package com.zaylope.registration.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "applicant_onboarding_sessions",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_onboarding_sessions_token_hash",
				columnNames = "token_hash"),
		indexes = @Index(name = "ix_onboarding_sessions_expires", columnList = "expires_at"))
public class ApplicantOnboardingSession {

	public enum FlowType {
		GOOGLE_COMPLETE_PROFILE,
		GOOGLE_LINK_ACCOUNT
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "onboarding_session_id", nullable = false, updatable = false)
	private Long onboardingSessionId;

	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "flow_type", nullable = false, length = 32)
	private FlowType flowType;

	@Column(name = "google_sub", nullable = false, length = 255)
	private String googleSub;

	@Column(name = "google_email", nullable = false, length = 254)
	private String googleEmail;

	@Column(name = "google_email_verified", nullable = false)
	private boolean googleEmailVerified;

	@Column(name = "google_hosted_domain", length = 253)
	private String googleHostedDomain;

	@Column(name = "google_name", length = 150)
	private String googleName;

	@Column(name = "google_picture_url", length = 512)
	private String googlePictureUrl;

	@Column(name = "target_applicant_id")
	private Long targetApplicantId;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant expiresAt;

	@Column(name = "consumed_at", columnDefinition = "TIMESTAMP(6)")
	private Instant consumedAt;

	@Column(name = "revoked_at", columnDefinition = "TIMESTAMP(6)")
	private Instant revokedAt;

	protected ApplicantOnboardingSession() {
	}

	public ApplicantOnboardingSession(
			String tokenHash,
			FlowType flowType,
			String googleSub,
			String googleEmail,
			boolean googleEmailVerified,
			String googleHostedDomain,
			String googleName,
			String googlePictureUrl,
			Long targetApplicantId,
			Instant createdAt,
			Instant expiresAt) {
		this.tokenHash = tokenHash;
		this.flowType = flowType;
		this.googleSub = googleSub;
		this.googleEmail = googleEmail;
		this.googleEmailVerified = googleEmailVerified;
		this.googleHostedDomain = googleHostedDomain;
		this.googleName = googleName;
		this.googlePictureUrl = googlePictureUrl;
		this.targetApplicantId = targetApplicantId;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	public Long getOnboardingSessionId() {
		return onboardingSessionId;
	}

	public FlowType getFlowType() {
		return flowType;
	}

	public String getGoogleSub() {
		return googleSub;
	}

	public String getGoogleEmail() {
		return googleEmail;
	}

	public boolean isGoogleEmailVerified() {
		return googleEmailVerified;
	}

	public String getGoogleHostedDomain() {
		return googleHostedDomain;
	}

	public String getGoogleName() {
		return googleName;
	}

	public String getGooglePictureUrl() {
		return googlePictureUrl;
	}

	public Long getTargetApplicantId() {
		return targetApplicantId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public boolean isActiveAt(Instant now) {
		return consumedAt == null && revokedAt == null && now.isBefore(expiresAt);
	}

	public void consume(Instant now) {
		this.consumedAt = now;
	}

	public void revoke(Instant now) {
		this.revokedAt = now;
	}
}
