package com.zaylope.registration.registration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "mobile_registration_locks")
public class ApplicantRegistrationLock {

	@Id
	@Column(name = "mobile_number", nullable = false, length = 32)
	private String mobileNumber;

	@Column(name = "applicant_id")
	private Long applicantId;

	@Column(name = "expires_at", columnDefinition = "TIMESTAMP(6)")
	private Instant expiresAt;

	protected ApplicantRegistrationLock() {
	}

	public ApplicantRegistrationLock(String mobileNumber) {
		this.mobileNumber = mobileNumber;
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void assign(Long applicantId, Instant expiresAt) {
		this.applicantId = applicantId;
		this.expiresAt = expiresAt;
	}

	public void markVerified(Long applicantId) {
		this.applicantId = applicantId;
		this.expiresAt = null;
	}
}
