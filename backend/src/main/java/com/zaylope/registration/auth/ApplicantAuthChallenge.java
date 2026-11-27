package com.zaylope.registration.auth;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.OtpRateLimitException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "applicant_auth_challenges",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_auth_challenge_applicant_purpose",
				columnNames = {"applicant_id", "purpose"}),
		indexes = @Index(name = "ix_auth_challenge_expiry", columnList = "expires_at"))
public class ApplicantAuthChallenge {

	public enum Purpose {
		LOGIN,
		DRAFT_RECOVERY,
		LEGACY_EMAIL_RECOVERY,
		LINK_GOOGLE
	}

	@Id
	@Column(name = "challenge_id", length = 36, nullable = false, updatable = false)
	private String challengeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "applicant_id", nullable = false)
	private Applicant applicant;

	@Enumerated(EnumType.STRING)
	@Column(name = "purpose", nullable = false, length = 24)
	private Purpose purpose;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_channel", nullable = false, length = 8)
	private ApplicantVerificationChannel channel;

	@Column(name = "destination_hash", nullable = false, length = 64)
	private String destinationHash;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "issuance_id", nullable = false, length = 36)
	private String issuanceId;

	@Column(name = "challenge_status", nullable = false, length = 24)
	private String status = "ACTIVE";

	@Column(name = "send_count", nullable = false)
	private int sendCount;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "send_window_started_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant sendWindowStartedAt;

	@Column(name = "last_sent_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant lastSentAt;

	@Column(name = "resend_available_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant resendAvailableAt;

	@Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant expiresAt;

	@Column(name = "consumed_at", columnDefinition = "TIMESTAMP(6)")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Version
	@Column(name = "row_version", nullable = false)
	private long version;

	protected ApplicantAuthChallenge() {
	}

	public ApplicantAuthChallenge(
			Applicant applicant,
			Purpose purpose,
			ApplicantVerificationChannel channel,
			String destinationHash,
			String codeHash,
			String issuanceId,
			Instant now,
			Instant expiresAt,
			Instant resendAvailableAt) {
		this.challengeId = UUID.randomUUID().toString();
		this.applicant = applicant;
		this.purpose = purpose;
		this.channel = channel;
		this.destinationHash = destinationHash;
		this.codeHash = codeHash;
		this.issuanceId = issuanceId;
		this.status = "ACTIVE";
		this.sendCount = 1;
		this.attemptCount = 0;
		this.sendWindowStartedAt = now;
		this.lastSentAt = now;
		this.resendAvailableAt = resendAvailableAt;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	public void reissue(
			ApplicantVerificationChannel channel,
			String destinationHash,
			String codeHash,
			String issuanceId,
			Instant now,
			Instant newExpiresAt,
			Instant newResendAvailableAt,
			Duration sendWindow,
			int maxSendsPerWindow) {
		if ("ACTIVE".equals(status) && now.isBefore(resendAvailableAt)) {
			throw new OtpRateLimitException(
					"OTP_RESEND_TOO_SOON",
					Math.max(1, Duration.between(now, resendAvailableAt).toSeconds()));
		}
		if (!now.isBefore(sendWindowStartedAt.plus(sendWindow))) {
			sendWindowStartedAt = now;
			sendCount = 0;
		}
		if (sendCount >= maxSendsPerWindow) {
			Instant nextWindow = sendWindowStartedAt.plus(sendWindow);
			throw new OtpRateLimitException(
					"OTP_RATE_LIMITED",
					Math.max(1, Duration.between(now, nextWindow).toSeconds()));
		}
		this.channel = channel;
		this.destinationHash = destinationHash;
		this.codeHash = codeHash;
		this.issuanceId = issuanceId;
		this.status = "ACTIVE";
		this.lastSentAt = now;
		this.resendAvailableAt = newResendAvailableAt;
		this.expiresAt = newExpiresAt;
		this.sendCount++;
		this.attemptCount = 0;
		this.consumedAt = null;
	}

	public String getChallengeId() {
		return challengeId;
	}

	public Applicant getApplicant() {
		return applicant;
	}

	public Purpose getPurpose() {
		return purpose;
	}

	public ApplicantVerificationChannel getChannel() {
		return channel;
	}

	public String getDestinationHash() {
		return destinationHash;
	}

	public String getCodeHash() {
		return codeHash;
	}

	public String getIssuanceId() {
		return issuanceId;
	}

	public String getStatus() {
		return status;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getResendAvailableAt() {
		return resendAvailableAt;
	}

	public boolean isExpiredAt(Instant now) {
		return !now.isBefore(expiresAt);
	}

	public boolean isConsumed() {
		return consumedAt != null;
	}

	public boolean hasExceededAttempts(int maxAttempts) {
		return attemptCount >= maxAttempts;
	}

	public void recordFailedAttempt(int maxAttempts) {
		attemptCount++;
		if (attemptCount >= maxAttempts) {
			status = "LOCKED";
		}
	}

	public void consume(Instant now) {
		this.consumedAt = now;
		this.status = "VERIFIED";
	}

	public void deliveryFailed(Instant now) {
		this.consumedAt = now;
		this.status = "DELIVERY_FAILED";
	}

	public void invalidate(Instant now) {
		this.consumedAt = now;
		this.status = "INVALIDATED";
	}
}
