package com.zaylope.registration.affiliate;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.AffiliateAccessDeniedException;
import com.zaylope.registration.config.RazorpayProperties;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.payment.PaymentTransactionRepository;
import java.time.Clock;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Single centralized, idempotent coordinator for automatic lifetime membership activation and
 * permanent Affiliate ID issuance after verified captured ₹599 payment.
 */
@Service
public class AffiliateMembershipActivationService {

	private final AffiliateIdService affiliateIdService;
	private final MembershipActivationPolicy activationPolicy;
	private final AffiliateMembershipPersistence persistence;

	public AffiliateMembershipActivationService(
			ApplicantRepository applicantRepository,
			AffiliateMembershipRepository membershipRepository,
			PaymentTransactionRepository paymentTransactionRepository,
			AffiliateIdService affiliateIdService,
			MembershipActivationPolicy activationPolicy,
			RazorpayProperties razorpayProperties,
			Clock clock) {
		this(
				affiliateIdService,
				activationPolicy,
				new AffiliateMembershipPersistence(
						applicantRepository,
						membershipRepository,
						paymentTransactionRepository,
						activationPolicy,
						razorpayProperties,
						clock));
	}

	@Autowired
	public AffiliateMembershipActivationService(
			AffiliateIdService affiliateIdService,
			MembershipActivationPolicy activationPolicy,
			AffiliateMembershipPersistence persistence) {
		this.affiliateIdService = affiliateIdService;
		this.activationPolicy = activationPolicy;
		this.persistence = persistence;
	}

	public record ActivationResult(
			AffiliateMembership membership,
			boolean newlyActivated,
			boolean restricted) {
	}

	/**
	 * Idempotently activates membership in three non-overlapping short transactions
	 * (precondition check -> isolated counter allocation -> atomic membership persist),
	 * holding at most one database connection per thread at any instant.
	 */
	public ActivationResult activateAfterPayment(long applicantId) {
		Optional<ActivationResult> preExisting = persistence.findExistingOrValidatePreconditions(applicantId);
		if (preExisting.isPresent()) {
			return preExisting.get();
		}

		String affiliateId = affiliateIdService.generateNextAffiliateId();
		String referralCode = affiliateIdService.generateUniqueReferralCode();
		return persistence.persistActivatedMembership(applicantId, affiliateId, referralCode);
	}

	public Optional<PaymentTransaction> findVerifiedCapturedTransaction(long applicantId) {
		return persistence.findVerifiedCapturedTransaction(applicantId);
	}

	public MembershipLifecycle.MembershipStatus deriveMembershipStatus(
			Applicant applicant,
			Optional<AffiliateMembership> membershipOpt) {
		if (activationPolicy.isAccountRestricted(applicant, membershipOpt)) {
			return MembershipLifecycle.MembershipStatus.REVOKED;
		}
		if (membershipOpt != null && membershipOpt.isPresent()
				&& "ACTIVE".equalsIgnoreCase(membershipOpt.get().getStatus())) {
			return MembershipLifecycle.MembershipStatus.ACTIVE;
		}
		if (applicant.isPaymentPaid()) {
			return MembershipLifecycle.MembershipStatus.PENDING_ACTIVATION;
		}
		return MembershipLifecycle.MembershipStatus.NOT_STARTED;
	}

	public MembershipLifecycle.AffiliateAccountStatus deriveAffiliateAccountStatus(
			Applicant applicant,
			Optional<AffiliateMembership> membershipOpt) {
		if ("REJECTED".equalsIgnoreCase(applicant.getApprovalStatus())) {
			return MembershipLifecycle.AffiliateAccountStatus.SUSPENDED;
		}
		if (membershipOpt == null || membershipOpt.isEmpty()) {
			return MembershipLifecycle.AffiliateAccountStatus.PENDING;
		}
		String rawStatus = membershipOpt.get().getStatus();
		if ("SUSPENDED".equalsIgnoreCase(rawStatus)) {
			return MembershipLifecycle.AffiliateAccountStatus.SUSPENDED;
		}
		if ("CLOSED".equalsIgnoreCase(rawStatus)) {
			return MembershipLifecycle.AffiliateAccountStatus.CLOSED;
		}
		if ("ACTIVE".equalsIgnoreCase(rawStatus)) {
			return MembershipLifecycle.AffiliateAccountStatus.ACTIVE;
		}
		return MembershipLifecycle.AffiliateAccountStatus.PENDING;
	}

	public void assertMembershipAccessible(Applicant applicant, Optional<AffiliateMembership> membershipOpt) {
		if (!activationPolicy.canAccessMembershipBenefits(applicant, membershipOpt)) {
			throw new AffiliateAccessDeniedException(
					"Active lifetime membership requires verified contact details, completed registration, and verified payment.");
		}
	}
}
