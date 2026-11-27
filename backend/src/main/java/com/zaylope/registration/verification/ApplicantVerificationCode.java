package com.zaylope.registration.verification;

import com.zaylope.registration.applicant.Applicant;
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
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
		name = "applicant_verification_codes",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_applicant_verification_channel",
		columnNames = {"applicant_id", "verification_channel"}),
		indexes = @Index(name = "ix_applicant_verification_expiry", columnList = "expires_at"))
public class ApplicantVerificationCode {

	@Id
	@Column(name = "challenge_id", length = 36, nullable = false, updatable = false)
	private String challengeId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "applicant_id", nullable = false)
	private Applicant applicant;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_channel", length = 8, nullable = false)
	private ApplicantVerificationChannel channel;

	@Column(name = "code_hash", length = 64, nullable = false)
	private String codeHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "resend_available_at", nullable = false)
	private Instant resendAvailableAt;

	@Column(name = "send_window_started_at", nullable = false)
	private Instant sendWindowStartedAt;

	@Column(name = "send_count", nullable = false)
	private int sendCount;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "issuance_id", length = 36)
	private String issuanceId;
	@Column(name = "destination_hash", length = 64)
	private String destinationHash;
	@Column(name = "purpose", nullable = false, length = 32)
	private String purpose = "APPLICANT_CONTACT";
	@Column(name = "challenge_status", nullable = false, length = 24)
	private String status = "ACTIVE";
	@Column(name = "last_sent_at")
	private Instant lastSentAt;
	@jakarta.persistence.Version
	@Column(name = "row_version", nullable = false)
	private long version;

	protected ApplicantVerificationCode() {
	}

	public ApplicantVerificationCode(
			Applicant applicant,
			ApplicantVerificationChannel channel,
			String codeHash,
			Instant createdAt,
			Instant expiresAt,
			Instant resendAvailableAt,
			int sendCount) {
		this.challengeId = UUID.randomUUID().toString();
		this.issuanceId = UUID.randomUUID().toString();
		this.lastSentAt = createdAt;
		this.applicant = applicant;
		this.channel = channel;
		this.codeHash = codeHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.resendAvailableAt = resendAvailableAt;
		this.sendWindowStartedAt = createdAt;
		this.sendCount = sendCount;
		this.attemptCount = 0;
	}

	public void resend(
			String newCodeHash,
			Instant now,
			Instant newExpiresAt,
			Instant newResendAvailableAt,
			Duration sendWindow,
			int maxSendsPerWindow) {
		if (now.isBefore(resendAvailableAt)) {
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
		codeHash = newCodeHash;
		issuanceId = UUID.randomUUID().toString();
		lastSentAt = now;
		status = "ACTIVE";
		expiresAt = newExpiresAt;
		resendAvailableAt = newResendAvailableAt;
		sendCount++;
		attemptCount = 0;
		consumedAt = null;
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

	public void recordFailedAttempt() {
		attemptCount++;
	}
	public void lock() { status = "LOCKED"; }

	public void consume(Instant now) {
		consumedAt = now;
		status = "VERIFIED";
	}

	public void invalidate(Instant now) {
		consumedAt = now;
		status = "INVALIDATED";
	}

	public void bind(String hash, String destinationHash) { this.codeHash = hash; this.destinationHash = destinationHash; }
	public boolean isActiveFor(String fingerprint) { return "ACTIVE".equals(status) && "APPLICANT_CONTACT".equals(purpose) && issuanceId != null && fingerprint.equals(destinationHash); }
	public void deliveryFailed(Instant now) { consumedAt = now; status = "DELIVERY_FAILED"; }
	public String getIssuanceId() { return issuanceId; }
	public String getDestinationHash() { return destinationHash; }
	public String getStatus() { return status; }
	public String getPurpose() { return purpose; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getLastSentAt() { return lastSentAt; }
	public Instant getResendAvailableAt() { return resendAvailableAt; }

	public String getCodeHash() {
		return codeHash;
	}

	public ApplicantVerificationChannel getChannel() {
		return channel;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}
}
