package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.auth.GoogleAuthDtos.GoogleAuthResponse;
import com.zaylope.registration.common.exception.DuplicateEmailException;
import com.zaylope.registration.common.exception.GoogleAuthUnavailableException;
import com.zaylope.registration.common.exception.GoogleIdentityConflictException;
import com.zaylope.registration.common.exception.MobileAlreadyVerifiedException;
import com.zaylope.registration.common.exception.MobileNumberAlreadyUsedException;
import com.zaylope.registration.common.exception.OnboardingSessionException;
import com.zaylope.registration.config.RegistrationProperties;
import com.zaylope.registration.registration.ApplicantRegistrationLock;
import com.zaylope.registration.registration.ApplicantRegistrationLockRepository;
import com.zaylope.registration.registration.IndianMobileNumber;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantContactMasker;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoogleAuthService {

	private final GoogleTokenVerifierService tokenVerifier;
	private final ApplicantRepository applicantRepository;
	private final ApplicantRegistrationLockRepository lockRepository;
	private final AffiliateMembershipRepository membershipRepository;
	private final ApplicantSessionService sessionService;
	private final ApplicantOnboardingSessionService onboardingSessionService;
	private final ApplicantAuthChallengeService authChallengeService;
	private final RegistrationProperties registrationProperties;
	private final com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository;
	private final com.zaylope.registration.affiliate.MembershipActivationPolicy activationPolicy;
	private final Clock clock;
	private com.zaylope.registration.common.validation.EmailContactPolicy emailPolicy;
	private com.zaylope.registration.common.validation.EmailDomainValidator emailDomains;
	private org.springframework.transaction.support.TransactionTemplate contactTransactions;

	@Autowired
	public void configureEmailValidation(com.zaylope.registration.common.validation.EmailContactPolicy policy,
			com.zaylope.registration.common.validation.EmailDomainValidator domains,
			org.springframework.transaction.PlatformTransactionManager transactionManager) {
		emailPolicy = policy;
		emailDomains = domains;
		contactTransactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
	}

	public GoogleAuthService(
			GoogleTokenVerifierService tokenVerifier,
			ApplicantRepository applicantRepository,
			ApplicantRegistrationLockRepository lockRepository,
			AffiliateMembershipRepository membershipRepository,
			ApplicantSessionService sessionService,
			ApplicantOnboardingSessionService onboardingSessionService,
			ApplicantAuthChallengeService authChallengeService,
			RegistrationProperties registrationProperties,
			Clock clock) {
		this(
				tokenVerifier,
				applicantRepository,
				lockRepository,
				membershipRepository,
				sessionService,
				onboardingSessionService,
				authChallengeService,
				registrationProperties,
				null,
				new com.zaylope.registration.affiliate.MembershipActivationPolicy(),
				clock);
	}

	@Autowired
	public GoogleAuthService(
			GoogleTokenVerifierService tokenVerifier,
			ApplicantRepository applicantRepository,
			ApplicantRegistrationLockRepository lockRepository,
			AffiliateMembershipRepository membershipRepository,
			ApplicantSessionService sessionService,
			ApplicantOnboardingSessionService onboardingSessionService,
			ApplicantAuthChallengeService authChallengeService,
			RegistrationProperties registrationProperties,
			com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository,
			com.zaylope.registration.affiliate.MembershipActivationPolicy activationPolicy,
			Clock clock) {
		this.tokenVerifier = tokenVerifier;
		this.applicantRepository = applicantRepository;
		this.lockRepository = lockRepository;
		this.membershipRepository = membershipRepository;
		this.sessionService = sessionService;
		this.onboardingSessionService = onboardingSessionService;
		this.authChallengeService = authChallengeService;
		this.registrationProperties = registrationProperties;
		this.paymentOrderRepository = paymentOrderRepository;
		this.activationPolicy = activationPolicy != null
				? activationPolicy
				: new com.zaylope.registration.affiliate.MembershipActivationPolicy();
		this.clock = clock;
	}

	public record AuthOutcome(
			GoogleAuthResponse response,
			String applicantSessionToken,
			String onboardingSessionToken,
			boolean clearApplicantSession,
			boolean clearOnboardingSession,
			boolean created) {}

	@Transactional
	public AuthOutcome authenticateWithGoogle(String credential, String existingApplicantToken, String existingOnboardingToken) {
		return authenticateWithGoogle(credential, "REGISTER", existingApplicantToken, existingOnboardingToken);
	}

	@Transactional
	public AuthOutcome authenticateWithGoogle(
			String credential,
			String intent,
			String existingApplicantToken,
			String existingOnboardingToken) {
		if (!tokenVerifier.isConfigured()) {
			throw new GoogleAuthUnavailableException();
		}

		boolean loginIntent = "LOGIN".equalsIgnoreCase(intent == null ? "" : intent.trim());

		var payloadOpt = tokenVerifier.verifyToken(credential);
		if (payloadOpt.isEmpty()) {
			return null;
		}

		if (existingOnboardingToken != null && !existingOnboardingToken.isBlank()) {
			onboardingSessionService.revoke(existingOnboardingToken);
		}

		GoogleTokenVerifierService.GoogleUserPayload payload = payloadOpt.get();
		String sub = payload.sub();
		String email = payload.email();
		Instant now = clock.instant();
		boolean authoritativeEmail = tokenVerifier.isAuthoritativeGoogleEmail(payload);

		// 1. Returning Google user identified strictly by Google sub
		Optional<Applicant> existingBySub = applicantRepository.findByGoogleSub(sub);
		if (existingBySub.isPresent()) {
			Applicant applicant = existingBySub.get();
			if (activationPolicy.isAccountRestricted(applicant, membershipRepository.findByApplicantId(applicant.getApplicantId()))) {
				throw new OnboardingSessionException("ACCOUNT_RESTRICTED", "This account is currently restricted.");
			}
			revalidateLegacyEmailProvenanceOnGoogleSignIn(applicant, email, authoritativeEmail, now);
			applicantRepository.saveAndFlush(applicant);

			ApplicantSessionScope scope = applicant.isContactVerified()
					? ApplicantSessionScope.AUTHENTICATED
					: ApplicantSessionScope.ONBOARDING;
			String sessionToken = sessionService.rotateSession(applicant, scope, existingApplicantToken);
			return new AuthOutcome(buildAuthenticatedResponse(applicant), sessionToken, null, false, true, false);
		}

		// Do not disclose account existence, link existing accounts, or auto-verify emails when Google email is unverified
		if (!payload.emailVerified()) {
			throw new OnboardingSessionException(
					"GOOGLE_EMAIL_UNVERIFIED",
					"Your Google email ownership could not be verified. Sign in with a verified contact or register manually.");
		}

		// 2. Check existing active/verified account by email (conflict / secure linking check)
		Optional<Applicant> existingVerifiedByEmail = applicantRepository.findByEmailAndEmailVerifiedAtIsNotNull(email);
		Optional<Applicant> existingActiveByEmail = existingVerifiedByEmail.isPresent()
				? existingVerifiedByEmail
				: applicantRepository.findByActiveEmailKeyForUpdate(email);

		if (existingActiveByEmail.isPresent()) {
			Applicant existing = existingActiveByEmail.get();
			if (existing.getGoogleSub() != null) {
				throw new GoogleIdentityConflictException(
						"This account is already connected to a different Google account. Please sign in with your linked method.");
			}

			if (existing.isUnverifiedUncommittedDraft()) {
				// Release abandoned or superseded unverified draft only without merging/trusting its unverified mobile
				if (existing.isPendingEmailExpiredAt(now) || authoritativeEmail) {
					existing.releasePendingEmailReservation();
					existing.releasePendingMobileReservation();
					applicantRepository.saveAndFlush(existing);
				} else {
					throw new DuplicateEmailException();
				}
			} else {
				// Existing verified or committed account without Google sub -> initiate secure linking challenge
				if (!existing.isMobileVerified() && !existing.isTrustedEmailVerified()) {
					throw new GoogleIdentityConflictException(
							"Please verify your existing account before connecting Google.");
				}
				String onboardingToken = onboardingSessionService.create(
						ApplicantOnboardingSession.FlowType.GOOGLE_LINK_ACCOUNT,
						payload,
						existing.getApplicantId());
				String channel = existing.isMobileVerified() ? "MOBILE" : "EMAIL";
				String masked = existing.isMobileVerified()
						? ApplicantContactMasker.maskMobile(existing.getMobileNumber())
						: ApplicantContactMasker.maskEmail(existing.getEmail());

				return new AuthOutcome(
						GoogleAuthResponse.linkingRequired(
								email,
								channel,
								masked,
								"An existing ZayloPe account uses this email. Confirm ownership with a verification code to connect Google.",
								sanitizePictureUrl(payload.pictureUrl())),
						null,
						onboardingToken,
						false,
						false,
						false);
			}
		}

		// 3. Distinction between LOGIN and REGISTER intent for unregistered verified Google emails
		if (loginIntent) {
			return new AuthOutcome(
					GoogleAuthResponse.accountNotFound(
							email,
							"This email address is not registered with ZayloPe.",
							sanitizePictureUrl(payload.pictureUrl())),
					null,
					null,
					false,
					true,
					false);
		}

		// 4. Brand-new Google registration -> create short-lived server-side onboarding session
		if (emailPolicy != null) emailPolicy.normalizeNewContact(email);
		String onboardingToken = onboardingSessionService.create(
				ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE,
				payload,
				null);
		return new AuthOutcome(
				GoogleAuthResponse.needsProfileCompletion(
						email,
						payload.name() != null && !payload.name().isBlank() ? payload.name() : "",
						authoritativeEmail,
						authoritativeEmail,
						"Enter your mobile number to finish setting up your ZayloPe account.",
						sanitizePictureUrl(payload.pictureUrl())),
				null,
				onboardingToken,
				false,
				false,
				false);
	}

	@Transactional(readOnly = true)
	public GoogleAuthResponse getOnboardingState(String onboardingToken) {
		ApplicantOnboardingSession session = onboardingSessionService.findActive(onboardingToken)
				.orElseThrow(() -> new OnboardingSessionException(
						"ONBOARDING_SESSION_EXPIRED",
						"Your Google sign-in session has expired. Please continue with Google again."));

		if (session.getFlowType() == ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE) {
			boolean authoritative = isAuthoritativeSession(session);
			return GoogleAuthResponse.needsProfileCompletion(
					session.getGoogleEmail(),
					session.getGoogleName() != null ? session.getGoogleName() : "",
					authoritative,
					authoritative,
					"Enter your mobile number to finish setting up your ZayloPe account.",
					sanitizePictureUrl(session.getGooglePictureUrl()));
		}

		Applicant target = applicantRepository.findById(session.getTargetApplicantId())
				.orElseThrow(() -> new OnboardingSessionException(
						"ONBOARDING_SESSION_INVALID",
						"Unable to locate the account to connect. Please try signing in again."));
		String channel = target.isMobileVerified() ? "MOBILE" : "EMAIL";
		String masked = target.isMobileVerified()
				? ApplicantContactMasker.maskMobile(target.getMobileNumber())
				: ApplicantContactMasker.maskEmail(target.getEmail());
		return GoogleAuthResponse.linkingRequired(
				session.getGoogleEmail(),
				channel,
				masked,
				"Confirm ownership of your existing account to connect Google.",
				sanitizePictureUrl(session.getGooglePictureUrl()));
	}

	public AuthOutcome completeProfile(String onboardingToken, String fullName, String rawMobile, String existingApplicantToken) {
		if (emailPolicy != null) {
			var pending = onboardingSessionService.findActive(onboardingToken)
					.filter(s -> s.getFlowType() == ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE)
					.orElseThrow(() -> new OnboardingSessionException("ONBOARDING_SESSION_EXPIRED", "Your Google sign-in session has expired."));
			String expectedEmail = emailPolicy.normalizeNewContact(pending.getGoogleEmail());
			emailDomains.validate(expectedEmail);
			return contactTransactions.execute(status -> completeProfileInTransaction(
					onboardingToken, fullName, rawMobile, existingApplicantToken, expectedEmail));
		}
		return completeProfileInTransaction(onboardingToken, fullName, rawMobile, existingApplicantToken, null);
	}

	private AuthOutcome completeProfileInTransaction(String onboardingToken, String fullName, String rawMobile,
			String existingApplicantToken, String expectedEmail) {
		ApplicantOnboardingSession session = onboardingSessionService.lockActiveForUpdate(onboardingToken)
				.filter(s -> s.getFlowType() == ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE)
				.orElseThrow(() -> new OnboardingSessionException(
						"ONBOARDING_SESSION_EXPIRED",
						"Your Google sign-in session has expired. Please continue with Google again."));

		Instant now = clock.instant();
		String sub = session.getGoogleSub();
		String email = com.zaylope.registration.common.validation.ApplicantInputValidator.normalizeEmail(
				session.getGoogleEmail());
		if (email == null) {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
					"email",
					com.zaylope.registration.common.validation.ApplicantInputValidator.EMAIL_MESSAGE);
		}
		if (emailPolicy != null) {
			email = emailPolicy.normalizeNewContact(email);
			if (!email.equals(expectedEmail)) throw new OnboardingSessionException(
					"ONBOARDING_SESSION_CHANGED", "Your Google sign-in details changed. Please retry.");
		}
		String normalizedMobile = IndianMobileNumber.normalize(rawMobile);
		String cleanName = com.zaylope.registration.common.validation.ApplicantInputValidator.normalizeFullName(fullName);
		if (cleanName == null) {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
					"fullName",
					com.zaylope.registration.common.validation.ApplicantInputValidator.FULL_NAME_MESSAGE);
		}

		// Guard against race if this Google sub was registered concurrently
		if (applicantRepository.existsByGoogleSub(sub)) {
			throw new GoogleIdentityConflictException("This Google account is already registered. Please log in.");
		}

		ApplicantRegistrationLock lock = lockRepository.findByMobileNumberForUpdate(normalizedMobile)
				.orElseGet(() -> lockRepository.saveAndFlush(new ApplicantRegistrationLock(normalizedMobile)));

		if (applicantRepository.existsByMobileNumberAndMobileVerifiedTrue(normalizedMobile)) {
			throw new MobileAlreadyVerifiedException();
		}

		Optional<Applicant> pendingMobileHolder = applicantRepository.findByPendingMobileKeyForUpdate(normalizedMobile);
		if (pendingMobileHolder.isPresent()) {
			Applicant existing = pendingMobileHolder.get();
			if (existing.isUnverifiedUncommittedDraft() && existing.isPendingMobileExpiredAt(now)) {
				existing.releasePendingMobileReservation();
				applicantRepository.saveAndFlush(existing);
			} else {
				throw new MobileNumberAlreadyUsedException();
			}
		}

		if (applicantRepository.findByEmailAndEmailVerifiedAtIsNotNull(email).isPresent()) {
			throw new DuplicateEmailException();
		}

		Optional<Applicant> activeEmailHolder = applicantRepository.findByActiveEmailKeyForUpdate(email);
		if (activeEmailHolder.isPresent()) {
			Applicant existing = activeEmailHolder.get();
			if (existing.isUnverifiedUncommittedDraft() && (existing.isPendingEmailExpiredAt(now) || isAuthoritativeSession(session))) {
				existing.releasePendingEmailReservation();
				existing.releasePendingMobileReservation();
				applicantRepository.saveAndFlush(existing);
			} else {
				throw new DuplicateEmailException();
			}
		}

		Applicant applicant = Applicant.createGoogleApplicant(
				cleanName,
				normalizedMobile,
				email,
				sub,
				email,
				session.getGooglePictureUrl());

		boolean authoritativeEmail = isAuthoritativeSession(session);
		Instant reservationExpiresAt = now.plus(registrationProperties.pendingReservationTtl());
		if (authoritativeEmail) {
			// Email ownership confirmed by Google authority; mobile remains unverified and is NOT reserved permanently
			applicant.markEmailVerified(now, Applicant.EMAIL_METHOD_GOOGLE);
		} else {
			// Third-party or unverified Google email requires independent OTP verification
			applicant.preparePendingRegistration(normalizedMobile, reservationExpiresAt);
		}

		applicantRepository.saveAndFlush(applicant);
		if (!authoritativeEmail) {
			lock.assign(applicant.getApplicantId(), reservationExpiresAt);
			lockRepository.saveAndFlush(lock);
		}

		session.consume(now);
		ApplicantSessionScope scope = authoritativeEmail
				? ApplicantSessionScope.AUTHENTICATED
				: ApplicantSessionScope.ONBOARDING;
		String sessionToken = sessionService.rotateSession(applicant, scope, existingApplicantToken);

		return new AuthOutcome(buildAuthenticatedResponse(applicant), sessionToken, null, false, true, true);
	}

	public ApplicantAuthChallengeService.LinkOtpDispatchResult sendLinkingOtp(String onboardingToken) {
		ApplicantOnboardingSession session = onboardingSessionService.findActive(onboardingToken)
				.filter(s -> s.getFlowType() == ApplicantOnboardingSession.FlowType.GOOGLE_LINK_ACCOUNT)
				.orElseThrow(() -> new OnboardingSessionException(
						"ONBOARDING_SESSION_EXPIRED",
						"Your linking session has expired. Please continue with Google again."));

		return authChallengeService.sendLinkingOtp(session.getTargetApplicantId());
	}

	@Transactional(noRollbackFor = {com.zaylope.registration.common.exception.InvalidAuthChallengeException.class,
			com.zaylope.registration.verification.OtpAttemptLimitExceededException.class})
	public AuthOutcome verifyLinkingOtp(String onboardingToken, String otpCode, String existingApplicantToken) {
		ApplicantOnboardingSession session = onboardingSessionService.lockActiveForUpdate(onboardingToken)
				.filter(s -> s.getFlowType() == ApplicantOnboardingSession.FlowType.GOOGLE_LINK_ACCOUNT)
				.orElseThrow(() -> new OnboardingSessionException(
						"ONBOARDING_SESSION_EXPIRED",
						"Your linking session has expired. Please continue with Google again."));

		Long targetId = session.getTargetApplicantId();
		if (targetId == null) {
			throw new OnboardingSessionException("ONBOARDING_SESSION_INVALID", "Invalid account linking session.");
		}

		authChallengeService.verifyLinkingOtp(targetId, otpCode);

		Applicant applicant = applicantRepository.findByIdForUpdate(targetId)
				.orElseThrow(() -> new OnboardingSessionException("ONBOARDING_SESSION_INVALID", "Account not found."));

		if (applicant.getGoogleSub() != null && !applicant.getGoogleSub().equals(session.getGoogleSub())) {
			throw new GoogleIdentityConflictException("This account is already connected to a different Google account.");
		}
		if (applicantRepository.existsByGoogleSub(session.getGoogleSub())) {
			throw new GoogleIdentityConflictException("This Google account is already connected to another ZayloPe account.");
		}

		Instant now = clock.instant();
		applicant.linkGoogleIdentity(session.getGoogleSub(), session.getGoogleEmail(), session.getGooglePictureUrl());
		if (isAuthoritativeSession(session) && session.getGoogleEmail().equals(applicant.getEmail())) {
			if (!applicant.isTrustedEmailVerified()) {
				applicant.markEmailVerified(now, Applicant.EMAIL_METHOD_GOOGLE);
			}
		}
		applicantRepository.saveAndFlush(applicant);
		session.consume(now);

		ApplicantSessionScope scope = applicant.isContactVerified()
				? ApplicantSessionScope.AUTHENTICATED
				: ApplicantSessionScope.ONBOARDING;
		String rotatedToken = sessionService.rotateSession(applicant, scope, existingApplicantToken);
		return new AuthOutcome(buildAuthenticatedResponse(applicant), rotatedToken, null, false, true, false);
	}

	@Transactional
	public void cancelOnboarding(String onboardingToken) {
		onboardingSessionService.revoke(onboardingToken);
	}

	public GoogleAuthResponse buildAuthenticatedResponse(Applicant applicant) {
		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(applicant.getApplicantId());
		boolean activeAffiliate = "ACTIVE".equalsIgnoreCase(
				membershipOpt.map(AffiliateMembership::getStatus).orElse(""));
		boolean restricted = "REJECTED".equalsIgnoreCase(applicant.getApprovalStatus())
				|| membershipOpt.map(AffiliateMembership::getStatus)
						.map(s -> "SUSPENDED".equalsIgnoreCase(s) || "CLOSED".equalsIgnoreCase(s) || "REVOKED".equalsIgnoreCase(s))
						.orElse(false);
		String authoritativeAffiliateId = (activeAffiliate && applicant.isPaymentPaid())
				? membershipOpt.get().getAffiliateId()
				: null;
		String eligibleStep = resolveEligibleStep(applicant, membershipOpt, activeAffiliate, restricted);
		String ref = applicant.getRegistrationId() != null
				? applicant.getRegistrationId()
				: (authoritativeAffiliateId != null ? authoritativeAffiliateId : "APP-" + applicant.getApplicantId());
		return GoogleAuthResponse.authenticated(
				ref,
				applicant.getFullName(),
				ApplicantContactMasker.maskMobile(applicant.getMobileNumber()),
				ApplicantContactMasker.maskEmail(applicant.getEmail()),
				applicant.isMobileVerified(),
				applicant.isTrustedEmailVerified(),
				applicant.getEmailVerificationMethod(),
				applicant.isRegistrationSubmitted(),
				applicant.getPaymentStatus(),
				applicant.getApprovalStatus(),
				authoritativeAffiliateId,
				eligibleStep);
	}

	public String resolveEligibleStep(Applicant applicant) {
		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(applicant.getApplicantId());
		boolean activeAffiliate = "ACTIVE".equalsIgnoreCase(
				membershipOpt.map(AffiliateMembership::getStatus).orElse(""));
		boolean restricted = "REJECTED".equalsIgnoreCase(applicant.getApprovalStatus())
				|| membershipOpt.map(AffiliateMembership::getStatus)
						.map(s -> "SUSPENDED".equalsIgnoreCase(s) || "CLOSED".equalsIgnoreCase(s) || "REVOKED".equalsIgnoreCase(s))
						.orElse(false);
		return resolveEligibleStep(applicant, membershipOpt, activeAffiliate, restricted);
	}

	private String resolveEligibleStep(
			Applicant applicant,
			Optional<AffiliateMembership> membershipOpt,
			boolean activeAffiliate,
			boolean restricted) {
		boolean hasPendingOrder = !activeAffiliate
				&& !restricted
				&& !applicant.isPaymentPaid()
				&& applicant.getApplicantId() != null
				&& paymentOrderRepository != null
				&& paymentOrderRepository
						.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(applicant.getApplicantId(), "CREATED")
						.isPresent();
		return activationPolicy.resolveDestination(applicant, membershipOpt, hasPendingOrder);
	}

	private void revalidateLegacyEmailProvenanceOnGoogleSignIn(
			Applicant applicant,
			String googleEmail,
			boolean authoritativeEmail,
			Instant now) {
		if (Applicant.EMAIL_METHOD_LEGACY_UNKNOWN.equals(applicant.getEmailVerificationMethod())) {
			if (authoritativeEmail && googleEmail.equals(applicant.getEmail())) {
				applicant.markEmailVerified(
						applicant.getEmailVerifiedAt() != null ? applicant.getEmailVerifiedAt() : now,
						Applicant.EMAIL_METHOD_GOOGLE);
			} else {
				// Do not trust unverified legacy timestamp when Google authority is lacking
				applicant.clearEmailVerification();
			}
		}
	}

	private boolean isAuthoritativeSession(ApplicantOnboardingSession session) {
		return tokenVerifier.isAuthoritativeGoogleEmail(new GoogleTokenVerifierService.GoogleUserPayload(
				session.getGoogleSub(),
				session.getGoogleEmail(),
				session.isGoogleEmailVerified(),
				session.getGoogleName(),
				session.getGooglePictureUrl(),
				session.getGoogleHostedDomain()));
	}

	private static String sanitizePictureUrl(String url) {
		if (url == null) {
			return null;
		}
		String trimmed = url.trim();
		if (trimmed.isEmpty() || trimmed.length() > 512) {
			return null;
		}
		return (trimmed.startsWith("https://") || trimmed.startsWith("http://")) ? trimmed : null;
	}
}
