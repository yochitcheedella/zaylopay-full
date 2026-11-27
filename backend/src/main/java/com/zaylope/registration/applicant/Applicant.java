package com.zaylope.registration.applicant;

import com.zaylope.registration.common.exception.GoogleIdentityConflictException;
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
import java.util.Locale;

@Entity
@Table(
		name = "applicants",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_applicants_registration_id",
						columnNames = "registration_id"),
				@UniqueConstraint(
						name = "uk_applicants_pending_mobile_key",
						columnNames = "pending_mobile_key"),
				@UniqueConstraint(
						name = "uk_applicants_active_email_key",
						columnNames = "active_email_key"),
				@UniqueConstraint(
						name = "uk_applicants_google_sub",
						columnNames = "google_sub"),
				@UniqueConstraint(
						name = "uk_applicants_affiliate_id",
						columnNames = "affiliate_id")
		},
		indexes = {
				@Index(name = "ix_applicants_mobile_number", columnList = "mobile_number"),
				@Index(name = "ix_applicants_email_address", columnList = "email_address"),
				@Index(name = "ix_applicants_google_sub", columnList = "google_sub")
		})
public class Applicant {

	public static final String EMAIL_METHOD_NONE = "NONE";
	public static final String EMAIL_METHOD_GOOGLE = "GOOGLE";
	public static final String EMAIL_METHOD_OTP = "EMAIL_OTP";
	public static final String EMAIL_METHOD_LEGACY_UNKNOWN = "LEGACY_UNKNOWN";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "registration_id", length = 20)
	private String registrationId;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(name = "mobile_number", nullable = false, length = 32)
	private String mobileNumber;

	@Column(name = "mobile_verified", nullable = false)
	private boolean mobileVerified;

	@Column(name = "pending_mobile_key", length = 32)
	private String pendingMobileKey;

	@Column(name = "pending_mobile_expires_at", columnDefinition = "TIMESTAMP(6)")
	private Instant pendingMobileExpiresAt;

	@Column(name = "email_address", nullable = false, length = 254)
	private String email;

	@Column(name = "email_case_sensitive", nullable = false)
	private boolean emailCaseSensitive;

	@Column(name = "active_email_key", length = 254)
	private String activeEmailKey;

	@Column(name = "pending_email_expires_at", columnDefinition = "TIMESTAMP(6)")
	private Instant pendingEmailExpiresAt;

	@Column(name = "google_sub", length = 255)
	private String googleSub;

	@Column(name = "auth_provider", length = 32, nullable = false)
	private String authProvider = "LOCAL";

	@Column(name = "google_email", length = 254)
	private String googleEmail;

	@Column(name = "google_picture_url", length = 512)
	private String googlePictureUrl;

	@Column(name = "mobile_verified_at")
	private Instant mobileVerifiedAt;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "email_verification_method", nullable = false, length = 24)
	private String emailVerificationMethod = EMAIL_METHOD_NONE;

	@Column(name = "privacy_consent_at")
	private Instant privacyConsentAt;

	@Column(name = "privacy_notice_version", length = 64)
	private String privacyNoticeVersion;

	@Column(name = "registration_submitted_at")
	private Instant registrationSubmittedAt;

	@Column(name = "payment_status", length = 24, nullable = false)
	private String paymentStatus = "PENDING";

	@Column(name = "approval_status", length = 24, nullable = false)
	private String approvalStatus = "PENDING";

	@Column(name = "approved_at")
	private Instant approvedAt;

	@Column(name = "rejected_at")
	private Instant rejectedAt;

	@Column(name = "rejection_reason", length = 500)
	private String rejectionReason;

	@Column(name = "affiliate_id", length = 32)
	private String affiliateId;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected Applicant() {
	}

	public Applicant(String fullName, String mobileNumber, String email) {
		this.fullName = fullName;
		this.mobileNumber = mobileNumber;
		this.email = normalizeEmail(email);
		this.emailCaseSensitive = true;
		this.authProvider = "LOCAL";
		this.emailVerificationMethod = EMAIL_METHOD_NONE;
	}

	public static Applicant createGoogleApplicant(
			String fullName,
			String mobileNumber,
			String email,
			String googleSub,
			String googleEmail,
			String googlePictureUrl) {
		Applicant applicant = new Applicant(fullName, mobileNumber, email);
		applicant.authProvider = "GOOGLE";
		applicant.googleSub = googleSub;
		applicant.googleEmail = normalizeEmail(googleEmail);
		applicant.googlePictureUrl = googlePictureUrl;
		return applicant;
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		updatedAt = Instant.now();
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public String getRegistrationId() {
		return registrationId;
	}

	public void setRegistrationId(String registrationId) {
		this.registrationId = registrationId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public String getFullName() {
		return fullName;
	}

	public void updateFullName(String fullName) {
		if (fullName != null && !fullName.isBlank()) {
			this.fullName = fullName.trim();
		}
	}

	public String getMobileNumber() {
		return mobileNumber;
	}

	public boolean isMobileVerified() {
		return mobileVerified;
	}

	public String getPendingMobileKey() {
		return pendingMobileKey;
	}

	public Instant getPendingMobileExpiresAt() {
		return pendingMobileExpiresAt;
	}

	public String getEmail() {
		return email;
	}

	public boolean isEmailCaseSensitive() {
		return emailCaseSensitive;
	}

	public String getActiveEmailKey() {
		return activeEmailKey;
	}

	public Instant getPendingEmailExpiresAt() {
		return pendingEmailExpiresAt;
	}

	public Instant getMobileVerifiedAt() {
		return mobileVerifiedAt;
	}

	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	public boolean isTrustedEmailVerified() {
		return emailVerifiedAt != null
				&& (EMAIL_METHOD_OTP.equals(emailVerificationMethod)
						|| EMAIL_METHOD_GOOGLE.equals(emailVerificationMethod));
	}

	public Instant getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public String getEmailVerificationMethod() {
		return emailVerificationMethod;
	}

	public boolean isContactVerified() {
		return mobileVerified || isTrustedEmailVerified();
	}

	public boolean hasValidPersonalDetails() {
		return fullName != null && !fullName.isBlank()
				&& mobileNumber != null && !mobileNumber.isBlank()
				&& email != null && !email.isBlank();
	}

	public Instant getPrivacyConsentAt() {
		return privacyConsentAt;
	}

	public String getPrivacyNoticeVersion() {
		return privacyNoticeVersion;
	}

	public boolean hasPrivacyConsent() {
		return privacyConsentAt != null;
	}

	public void recordPrivacyConsent(Instant consentedAt, String noticeVersion) {
		this.privacyConsentAt = consentedAt;
		this.privacyNoticeVersion = noticeVersion;
	}

	public Instant getRegistrationSubmittedAt() {
		return registrationSubmittedAt;
	}

	public String getRegistrationStatus() {
		return registrationSubmittedAt == null ? "DRAFT" : "SUBMITTED";
	}

	public boolean isRegistrationSubmitted() {
		return registrationSubmittedAt != null;
	}

	public void markRegistrationSubmitted(Instant submittedAt) {
		if (registrationSubmittedAt == null) {
			registrationSubmittedAt = submittedAt;
		}
		if (!mobileVerified) {
			pendingMobileKey = null;
			pendingMobileExpiresAt = null;
		}
	}

	public void markMobileVerified(Instant verifiedAt) {
		mobileVerified = true;
		mobileVerifiedAt = verifiedAt;
		pendingMobileKey = null;
		pendingMobileExpiresAt = null;
		if ("GOOGLE".equals(this.authProvider)) {
			this.authProvider = "BOTH";
		}
	}

	public void markEmailVerified(Instant verifiedAt) {
		markEmailVerified(verifiedAt, EMAIL_METHOD_OTP);
	}

	public void markEmailVerified(Instant verifiedAt, String method) {
		emailVerifiedAt = verifiedAt;
		emailVerificationMethod = (method == null || method.isBlank()) ? EMAIL_METHOD_OTP : method;
		activeEmailKey = normalizeEmail(email);
		pendingEmailExpiresAt = null;
		if (!mobileVerified) {
			pendingMobileKey = null;
			pendingMobileExpiresAt = null;
		}
	}

	public void clearEmailVerification() {
		emailVerifiedAt = null;
		emailVerificationMethod = EMAIL_METHOD_NONE;
	}

	public void changeMobileNumber(String newMobileNumber) {
		changeMobileNumber(newMobileNumber, null);
	}

	public void changeMobileNumber(String newMobileNumber, Instant reservationExpiresAt) {
		if (!mobileNumber.equals(newMobileNumber)) {
			mobileNumber = newMobileNumber;
			mobileVerified = false;
			mobileVerifiedAt = null;
			pendingMobileKey = newMobileNumber;
			pendingMobileExpiresAt = reservationExpiresAt;
		}
	}

	public void changeEmailAddress(String newEmail) {
		changeEmailAddress(newEmail, null);
	}

	public void changeEmailAddress(String newEmail, Instant reservationExpiresAt) {
		String normalized = normalizeEmail(newEmail);
		if (!email.equals(normalized)) {
			email = normalized;
			emailCaseSensitive = true;
			emailVerifiedAt = null;
			emailVerificationMethod = EMAIL_METHOD_NONE;
			activeEmailKey = normalized;
			pendingEmailExpiresAt = reservationExpiresAt;
		}
	}

	public void preparePendingRegistration(String normalizedMobileNumber) {
		preparePendingRegistration(normalizedMobileNumber, null);
	}

	public void preparePendingRegistration(String normalizedMobileNumber, Instant reservationExpiresAt) {
		pendingMobileKey = normalizedMobileNumber;
		pendingMobileExpiresAt = reservationExpiresAt;
		activeEmailKey = normalizeEmail(email);
		pendingEmailExpiresAt = reservationExpiresAt;
		mobileVerified = false;
		mobileVerifiedAt = null;
	}

	public boolean isUnverifiedUncommittedDraft() {
		return !mobileVerified
				&& emailVerifiedAt == null
				&& registrationSubmittedAt == null
				&& !"PAID".equalsIgnoreCase(paymentStatus)
				&& !"APPROVED".equalsIgnoreCase(approvalStatus);
	}

	public boolean isPendingMobileExpiredAt(Instant now) {
		return !mobileVerified
				&& (pendingMobileExpiresAt == null || !now.isBefore(pendingMobileExpiresAt));
	}

	public boolean isPendingEmailExpiredAt(Instant now) {
		return emailVerifiedAt == null
				&& (pendingEmailExpiresAt == null || !now.isBefore(pendingEmailExpiresAt));
	}

	public void releasePendingMobileReservation() {
		this.pendingMobileKey = null;
		this.pendingMobileExpiresAt = null;
	}

	public void releasePendingEmailReservation() {
		if (this.emailVerifiedAt == null) {
			this.activeEmailKey = null;
			this.pendingEmailExpiresAt = null;
		}
	}

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public boolean isPaymentPaid() {
		return "PAID".equalsIgnoreCase(paymentStatus);
	}

	public void markPaymentPaid() {
		this.paymentStatus = "PAID";
	}

	public String getApprovalStatus() {
		return approvalStatus;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public Instant getRejectedAt() {
		return rejectedAt;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public String getAffiliateId() {
		return affiliateId;
	}

	public void markApproved(String affiliateId, Instant now) {
		this.approvalStatus = "APPROVED";
		this.approvedAt = now;
		this.rejectedAt = null;
		this.rejectionReason = null;
		this.affiliateId = affiliateId;
	}

	public void markRejected(String reason, Instant now) {
		this.approvalStatus = "REJECTED";
		this.rejectedAt = now;
		this.rejectionReason = reason;
	}

	public String getGoogleSub() {
		return googleSub;
	}

	public String getAuthProvider() {
		return authProvider;
	}

	public String getGoogleEmail() {
		return googleEmail;
	}

	public String getGooglePictureUrl() {
		return googlePictureUrl;
	}

	public void linkGoogleIdentity(String googleSub, String googleEmail, String googlePictureUrl) {
		if (this.googleSub != null && !this.googleSub.equals(googleSub)) {
			throw new GoogleIdentityConflictException(
					"This account is already connected to a different Google account.");
		}
		this.googleSub = googleSub;
		this.googleEmail = normalizeEmail(googleEmail);
		this.googlePictureUrl = googlePictureUrl;
		if ("LOCAL".equals(this.authProvider)) {
			this.authProvider = "BOTH";
		} else if (this.authProvider == null || this.authProvider.isBlank()) {
			this.authProvider = "GOOGLE";
		}
	}

	public void setAuthProvider(String authProvider) {
		this.authProvider = authProvider;
	}

	private static String normalizeEmail(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		int atIndex = trimmed.lastIndexOf('@');
		if (atIndex < 0 || atIndex == trimmed.length() - 1) {
			return trimmed;
		}
		return trimmed.substring(0, atIndex + 1)
				+ trimmed.substring(atIndex + 1).toLowerCase(Locale.ROOT);
	}
}
