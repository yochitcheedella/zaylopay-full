package com.zaylope.registration.applicant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "applicant_bank_details",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_applicant_bank_details_applicant", columnNames = "applicant_id")
		})
public class ApplicantBankDetails {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "bank_detail_id", nullable = false, updatable = false)
	private Long bankDetailId;

	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "account_holder_name", nullable = false, length = 150)
	private String accountHolderName;

	@Column(name = "bank_name", nullable = false, length = 120)
	private String bankName;

	@Column(name = "account_number_ciphertext", nullable = false, length = 512)
	private String accountNumberCiphertext;

	@Column(name = "account_number_last4", nullable = false, length = 4)
	private String accountNumberLast4;

	@Column(name = "masked_account_number", nullable = false, length = 32)
	private String maskedAccountNumber;

	@Column(name = "ifsc_code", nullable = false, length = 16)
	private String ifscCode;

	@Column(name = "consent_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant consentAt;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected ApplicantBankDetails() {
	}

	public ApplicantBankDetails(
			Long applicantId,
			String accountHolderName,
			String bankName,
			String accountNumberCiphertext,
			String accountNumberLast4,
			String maskedAccountNumber,
			String ifscCode) {
		this.applicantId = applicantId;
		this.accountHolderName = accountHolderName;
		this.bankName = bankName;
		this.accountNumberCiphertext = accountNumberCiphertext;
		this.accountNumberLast4 = accountNumberLast4;
		this.maskedAccountNumber = maskedAccountNumber;
		this.ifscCode = ifscCode;
		this.consentAt = Instant.now();
	}

	public void updateDetails(
			String accountHolderName,
			String bankName,
			String accountNumberCiphertext,
			String accountNumberLast4,
			String maskedAccountNumber,
			String ifscCode) {
		this.accountHolderName = accountHolderName;
		this.bankName = bankName;
		if (accountNumberCiphertext != null && !accountNumberCiphertext.isBlank()) {
			this.accountNumberCiphertext = accountNumberCiphertext;
			this.accountNumberLast4 = accountNumberLast4;
			this.maskedAccountNumber = maskedAccountNumber;
		}
		this.ifscCode = ifscCode;
		this.consentAt = Instant.now();
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.consentAt == null) {
			this.consentAt = now;
		}
	}

	@PreUpdate
	void updateTimestamp() {
		this.updatedAt = Instant.now();
	}

	public Long getBankDetailId() {
		return bankDetailId;
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public String getAccountHolderName() {
		return accountHolderName;
	}

	public String getBankName() {
		return bankName;
	}

	public String getAccountNumberCiphertext() {
		return accountNumberCiphertext;
	}

	public String getAccountNumberLast4() {
		return accountNumberLast4;
	}

	public String getMaskedAccountNumber() {
		return maskedAccountNumber;
	}

	public String getIfscCode() {
		return ifscCode;
	}

	public Instant getConsentAt() {
		return consentAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
