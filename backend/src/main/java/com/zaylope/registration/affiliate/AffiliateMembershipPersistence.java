package com.zaylope.registration.affiliate;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.config.RazorpayProperties;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.payment.PaymentTransactionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional persistence boundary for eligibility validation and atomic AffiliateMembership creation.
 * Separated from AffiliateIdService counter allocation so each DB connection is held for only one short transaction.
 */
@Service
public class AffiliateMembershipPersistence {

	private static final Logger LOGGER = LoggerFactory.getLogger(AffiliateMembershipPersistence.class);

	private final ApplicantRepository applicantRepository;
	private final AffiliateMembershipRepository membershipRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final MembershipActivationPolicy activationPolicy;
	private final RazorpayProperties razorpayProperties;
	private final Clock clock;

	public AffiliateMembershipPersistence(
			ApplicantRepository applicantRepository,
			AffiliateMembershipRepository membershipRepository,
			PaymentTransactionRepository paymentTransactionRepository,
			MembershipActivationPolicy activationPolicy,
			RazorpayProperties razorpayProperties,
			Clock clock) {
		this.applicantRepository = applicantRepository;
		this.membershipRepository = membershipRepository;
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.activationPolicy = activationPolicy;
		this.razorpayProperties = razorpayProperties;
		this.clock = clock == null ? Clock.systemUTC() : clock;
	}

	@Transactional(readOnly = true)
	public Optional<AffiliateMembershipActivationService.ActivationResult> findExistingOrValidatePreconditions(
			long applicantId) {
		Applicant applicant = applicantRepository.findById(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);

		Optional<AffiliateMembership> existingOpt = membershipRepository.findByApplicantId(applicantId);
		if (existingOpt.isPresent()) {
			boolean restricted = activationPolicy.isAccountRestricted(applicant, existingOpt);
			return Optional.of(new AffiliateMembershipActivationService.ActivationResult(
					existingOpt.get(), false, restricted));
		}

		Optional<PaymentTransaction> capturedTxOpt = findVerifiedCapturedTransaction(applicantId);
		activationPolicy.validateEligibilityForActivation(
				applicant,
				existingOpt,
				capturedTxOpt,
				razorpayProperties.amountPaise(),
				razorpayProperties.currency());

		return Optional.empty();
	}

	@Transactional
	public AffiliateMembershipActivationService.ActivationResult persistActivatedMembership(
			long applicantId,
			String affiliateId,
			String referralCode) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseGet(() -> applicantRepository.findById(applicantId)
						.orElseThrow(ApplicantNotFoundException::new));

		Optional<AffiliateMembership> existingOpt = membershipRepository.findByApplicantIdForUpdate(applicantId);
		if (existingOpt.isEmpty()) {
			existingOpt = membershipRepository.findByApplicantId(applicantId);
		}
		if (existingOpt.isPresent()) {
			AffiliateMembership existing = existingOpt.get();
			boolean restricted = activationPolicy.isAccountRestricted(applicant, existingOpt);
			return new AffiliateMembershipActivationService.ActivationResult(existing, false, restricted);
		}

		Optional<PaymentTransaction> capturedTxOpt = findVerifiedCapturedTransaction(applicantId);
		activationPolicy.validateEligibilityForActivation(
				applicant,
				existingOpt,
				capturedTxOpt,
				razorpayProperties.amountPaise(),
				razorpayProperties.currency());

		Instant now = clock.instant();
		AffiliateMembership created = new AffiliateMembership(
				applicantId,
				affiliateId,
				referralCode,
				null,
				now,
				"LIFETIME");

		try {
			AffiliateMembership saved = membershipRepository.saveAndFlush(created);
			LOGGER.info("Activated lifetime membership for applicantId={} with affiliateId={}",
					applicantId, saved.getAffiliateId());
			return new AffiliateMembershipActivationService.ActivationResult(saved, true, false);
		} catch (DataIntegrityViolationException race) {
			AffiliateMembership raced = membershipRepository.findByApplicantId(applicantId)
					.orElseThrow(() -> race);
			return new AffiliateMembershipActivationService.ActivationResult(
					raced, false, activationPolicy.isAccountRestricted(applicant, Optional.of(raced)));
		}
	}

	public Optional<PaymentTransaction> findVerifiedCapturedTransaction(long applicantId) {
		long requiredAmount = razorpayProperties.amountPaise();
		String requiredCurrency = razorpayProperties.currency();
		return paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId)
				.stream()
				.filter(tx -> "CAPTURED".equalsIgnoreCase(tx.getStatus())
						&& tx.isSignatureVerified()
						&& tx.getAmountPaise() != null
						&& tx.getAmountPaise() == requiredAmount
						&& requiredCurrency.equalsIgnoreCase(tx.getCurrency()))
				.findFirst();
	}
}
