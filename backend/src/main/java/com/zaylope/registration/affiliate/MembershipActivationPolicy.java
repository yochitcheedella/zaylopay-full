package com.zaylope.registration.affiliate;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.common.exception.AffiliateAccessDeniedException;
import com.zaylope.registration.common.exception.ApplicationNotSubmittedException;
import com.zaylope.registration.common.exception.OnboardingConsentRequiredException;
import com.zaylope.registration.common.exception.PaymentRequiredForApprovalException;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Centralizes automatic membership activation and access eligibility rules so future
 * administrative review or compliance policies can be toggled in one place without
 * rewriting registration or payment workflows.
 */
@Component
public class MembershipActivationPolicy {

	public boolean isAdminApprovalRequired() {
		return false;
	}

	public boolean isAccountRestricted(Applicant applicant, Optional<AffiliateMembership> membershipOpt) {
		if (applicant == null) {
			return true;
		}
		if ("REJECTED".equalsIgnoreCase(applicant.getApprovalStatus())) {
			return true;
		}
		if (membershipOpt != null && membershipOpt.isPresent()) {
			String status = membershipOpt.get().getStatus();
			return "SUSPENDED".equalsIgnoreCase(status)
					|| "CLOSED".equalsIgnoreCase(status)
					|| "REVOKED".equalsIgnoreCase(status);
		}
		return false;
	}

	public void validateEligibilityForActivation(
			Applicant applicant,
			Optional<AffiliateMembership> existingMembership,
			Optional<PaymentTransaction> capturedTxOpt,
			long requiredAmountPaise,
			String requiredCurrency) {
		if (isAccountRestricted(applicant, existingMembership)) {
			throw new AffiliateAccessDeniedException(
					"Your account is currently restricted from membership activation. Contact support@zaylope.com.");
		}
		if (!applicant.hasValidPersonalDetails()) {
			throw new IllegalStateException("Valid personal details are required before membership activation.");
		}
		if (!applicant.isRegistrationSubmitted()) {
			throw new ApplicationNotSubmittedException();
		}
		if (!applicant.hasPrivacyConsent()) {
			throw new OnboardingConsentRequiredException();
		}
		if (!applicant.isContactVerified()) {
			throw new ApplicantVerificationRequiredException();
		}
		if (!applicant.isPaymentPaid() || capturedTxOpt == null || capturedTxOpt.isEmpty()) {
			throw new PaymentRequiredForApprovalException(
					"A verified and captured ₹599 payment is required before activating membership.");
		}

		PaymentTransaction tx = capturedTxOpt.get();
		if (!"CAPTURED".equalsIgnoreCase(tx.getStatus())
				|| !tx.isSignatureVerified()
				|| tx.getAmountPaise() == null
				|| tx.getAmountPaise() != requiredAmountPaise
				|| !requiredCurrency.equalsIgnoreCase(tx.getCurrency())) {
			throw new PaymentRequiredForApprovalException(
					"Confirmed ₹599 INR captured payment verification is required before activating membership.");
		}
	}

	public boolean canAccessMembershipBenefits(Applicant applicant, Optional<AffiliateMembership> membershipOpt) {
		if (applicant == null || membershipOpt == null || membershipOpt.isEmpty()) {
			return false;
		}
		if (isAccountRestricted(applicant, membershipOpt)) {
			return false;
		}
		AffiliateMembership membership = membershipOpt.get();
		return applicant.isPaymentPaid()
				&& applicant.isContactVerified()
				&& applicant.isRegistrationSubmitted()
				&& "ACTIVE".equalsIgnoreCase(membership.getStatus())
				&& membership.getAffiliateId() != null
				&& !membership.getAffiliateId().isBlank();
	}

	/**
	 * Canonical 4-step destination resolver shared across manual login, Google login,
	 * session restoration, and membership status endpoints.
	 */
	public String resolveDestination(
			Applicant applicant,
			Optional<AffiliateMembership> membershipOpt,
			boolean hasPendingPaymentOrder) {
		if (isAccountRestricted(applicant, membershipOpt)) {
			return "RESTRICTED";
		}
		if (canAccessMembershipBenefits(applicant, membershipOpt)) {
			return "DASHBOARD";
		}
		if (applicant.isPaymentPaid()) {
			return "ACTIVATION_RECOVERY";
		}
		if (!applicant.hasValidPersonalDetails()) {
			return "ACCOUNT";
		}
		if (!applicant.isContactVerified()) {
			return "VERIFY";
		}
		if (hasPendingPaymentOrder) {
			return "PAYMENT_STATUS";
		}
		return "PAYMENT";
	}
}
