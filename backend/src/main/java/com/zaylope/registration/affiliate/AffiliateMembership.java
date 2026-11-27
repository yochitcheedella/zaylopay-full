package com.zaylope.registration.affiliate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "affiliate_memberships", uniqueConstraints = {
		@UniqueConstraint(name = "uk_affiliate_memberships_applicant", columnNames = "applicant_id"),
		@UniqueConstraint(name = "uk_affiliate_memberships_affiliate_id", columnNames = "affiliate_id"),
		@UniqueConstraint(name = "uk_affiliate_memberships_referral_code", columnNames = "referral_code")
}, indexes = {
		@Index(name = "ix_affiliate_memberships_status", columnList = "status")
})
public class AffiliateMembership {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "membership_id", nullable = false, updatable = false)
	private Long membershipId;

	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "affiliate_id", nullable = false, length = 32)
	private String affiliateId;

	@Column(name = "referral_code", nullable = false, length = 32)
	private String referralCode;

	@Column(name = "status", nullable = false, length = 24)
	private String status = "ACTIVE";

	@Column(name = "tier", nullable = false, length = 24)
	private String tier = "AFFILIATE";

	@Column(name = "approval_id")
	private Long approvalId;

	@Column(name = "activated_at", nullable = false)
	private Instant activatedAt;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected AffiliateMembership() {
	}

	public AffiliateMembership(Long applicantId, String affiliateId, String referralCode, Long approvalId,
			Instant activatedAt) {
		this(applicantId, affiliateId, referralCode, approvalId, activatedAt, "LIFETIME");
	}

	public AffiliateMembership(
			Long applicantId,
			String affiliateId,
			String referralCode,
			Long approvalId,
			Instant activatedAt,
			String tier) {
		this.applicantId = applicantId;
		this.affiliateId = affiliateId;
		this.referralCode = referralCode;
		this.approvalId = approvalId;
		this.activatedAt = activatedAt;
		this.status = "ACTIVE";
		this.tier = (tier == null || tier.isBlank()) ? "LIFETIME" : tier;
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.activatedAt == null) {
			this.activatedAt = now;
		}
	}

	@PreUpdate
	void updateTimestamp() {
		this.updatedAt = Instant.now();
	}

	public Long getMembershipId() {
		return membershipId;
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public String getAffiliateId() {
		return affiliateId;
	}

	public String getReferralCode() {
		return referralCode;
	}

	public String getStatus() {
		return status;
	}

	public String getTier() {
		return tier;
	}

	public Long getApprovalId() {
		return approvalId;
	}

	public Instant getActivatedAt() {
		return activatedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
