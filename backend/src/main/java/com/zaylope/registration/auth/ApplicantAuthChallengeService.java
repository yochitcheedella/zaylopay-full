package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.affiliate.MembershipActivationPolicy;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.InvalidAuthChallengeException;
import com.zaylope.registration.common.exception.InvalidMobileNumberException;
import com.zaylope.registration.config.VerificationProperties;
import com.zaylope.registration.registration.IndianMobileNumber;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantContactMasker;
import com.zaylope.registration.verification.ApplicantOtpHasher;
import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import com.zaylope.registration.verification.OtpAbuseGuard;
import com.zaylope.registration.verification.OtpAttemptLimitExceededException;
import com.zaylope.registration.verification.OtpRateLimitException;
import com.zaylope.registration.verification.VerificationDeliveryUnavailableException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class ApplicantAuthChallengeService {

	private static final int OTP_BOUND = 1_000_000;

	private final ApplicantRepository applicantRepository;
	private final AffiliateMembershipRepository membershipRepository;
	private final MembershipActivationPolicy activationPolicy;
	private final ApplicantAuthChallengeRepository challengeRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final VerificationProperties properties;
	private final MobileOtpDelivery mobileDelivery;
	private final EmailOtpDelivery emailDelivery;
	private final ApplicantOtpHasher otpHasher;
	private final OtpAbuseGuard abuseGuard;
	private final ApplicantSessionService sessionService;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public ApplicantAuthChallengeService(
			ApplicantRepository applicantRepository,
			AffiliateMembershipRepository membershipRepository,
			MembershipActivationPolicy activationPolicy,
			ApplicantAuthChallengeRepository challengeRepository,
			ApplicationEventPublisher eventPublisher,
			VerificationProperties properties,
			MobileOtpDelivery mobileDelivery,
			EmailOtpDelivery emailDelivery,
			ApplicantOtpHasher otpHasher,
			OtpAbuseGuard abuseGuard,
			ApplicantSessionService sessionService,
			Clock clock) {
		this.applicantRepository = applicantRepository;
		this.membershipRepository = membershipRepository;
		this.activationPolicy = activationPolicy;
		this.challengeRepository = challengeRepository;
		this.eventPublisher = eventPublisher;
		this.properties = properties;
		this.mobileDelivery = mobileDelivery;
		this.emailDelivery = emailDelivery;
		this.otpHasher = otpHasher;
		this.abuseGuard = abuseGuard;
		this.sessionService = sessionService;
		this.clock = clock;
	}

	@org.springframework.beans.factory.annotation.Value("${app.auth.explicit-login-outcomes:false}")
	private boolean explicitLoginOutcomes;

	public record LoginOtpRequestResult(
			boolean accepted,
			String challengeId,
			long resendAfterSeconds,
			long expiresInSeconds,
			Instant serverTime,
			Instant expiresAt,
			Instant resendAvailableAt,
			String message, String outcome) {
        public LoginOtpRequestResult(boolean accepted, String challengeId, long resendAfterSeconds, long expiresInSeconds,
                Instant serverTime, Instant expiresAt, Instant resendAvailableAt, String message) {
            this(accepted, challengeId, resendAfterSeconds, expiresInSeconds, serverTime, expiresAt, resendAvailableAt, message, null);
        }
    }

	public record LoginOtpVerifyResult(
			Applicant applicant,
			ApplicantSessionScope sessionScope,
			String sessionToken,
			boolean draftRecovered) {}

	public record LinkOtpDispatchResult(
			String challengeId,
			ApplicantVerificationChannel channel,
			String maskedDestination,
			long resendAfterSeconds,
			Instant expiresAt,
			Instant resendAvailableAt,
			String deliveryMode) {}

	@Transactional(noRollbackFor = VerificationDeliveryUnavailableException.class)
	public LoginOtpRequestResult requestLoginOtp(String rawIdentifier) {
		return requestContactOtp(rawIdentifier, false);
	}

	@Transactional(noRollbackFor = VerificationDeliveryUnavailableException.class)
	public LoginOtpRequestResult requestRegistrationRecoveryOtp(String rawIdentifier) {
		return requestContactOtp(rawIdentifier, true);
	}

	private LoginOtpRequestResult requestContactOtp(String rawIdentifier, boolean recovery) {
		String trimmed = rawIdentifier == null ? "" : rawIdentifier.trim();
		boolean isEmail = trimmed.contains("@");
		String normalizedDestination = normalizeIdentifier(trimmed, isEmail);
		ApplicantVerificationChannel channel = isEmail
				? ApplicantVerificationChannel.EMAIL
				: ApplicantVerificationChannel.MOBILE;

		// A channel-wide outage is independent of account existence: fail uniformly instead of promising a code.
		if (!properties.hasOtpHashKey() || (isEmail ? !properties.emailEnabled() : !properties.mobileEnabled())) {
			throw new VerificationDeliveryUnavailableException();
		}

		// Enforce durable destination & IP send throttling uniformly before account lookup
		abuseGuard.check(0L, channel, abuseDestination(normalizedDestination, isEmail), clientIp(), true);

		Instant now = clock.instant();

		Optional<ResolvedLoginTarget> targetOpt = resolveLoginTarget(normalizedDestination, isEmail, now, recovery);

		if (explicitLoginOutcomes && !recovery && targetOpt.isEmpty()) {
            Optional<Applicant> existing = isEmail ? applicantRepository.findFirstByEmailOrderByApplicantIdDesc(normalizedDestination)
                    : applicantRepository.findFirstByMobileNumberOrderByApplicantIdDesc(normalizedDestination);
            existing = existing.filter(a -> matchesContact(a, normalizedDestination, isEmail));
            return explicitResult(existing.isEmpty() ? "ACCOUNT_NOT_FOUND" : isAccountRestricted(existing.get()) ? "ACCOUNT_UNAVAILABLE" : "CONTACT_VERIFICATION_REQUIRED", now);
        }
        if (explicitLoginOutcomes && !recovery && targetOpt.isPresent() && isAccountRestricted(targetOpt.get().applicant())) {
            return explicitResult("ACCOUNT_UNAVAILABLE", now);
        }
        if (targetOpt.isEmpty() || isAccountRestricted(targetOpt.get().applicant())) {
			return genericDispatchResponse(UUID.randomUUID().toString(), now);
		}

		ResolvedLoginTarget target = targetOpt.get();

		ApplicantAuthChallenge challenge;
		try {
			challenge = issueChallenge(
				target.applicant(),
				target.purpose(),
				channel,
				challengeDestination(target.applicant(), normalizedDestination, isEmail),
				now);
		} catch (OtpRateLimitException privateAccountLimit) {
			if (explicitLoginOutcomes && !recovery) return explicitResult("REQUEST_LIMITED", now);
			// Applicant/challenge quotas must not disclose an account through public status differences.
			return genericDispatchResponse(UUID.randomUUID().toString(), now);
		}

		if (explicitLoginOutcomes && !recovery) {
            return new LoginOtpRequestResult(true, challenge.getIssuanceId(),
                    Math.max(0, Duration.between(now, challenge.getResendAvailableAt()).toSeconds()),
                    Math.max(0, Duration.between(now, challenge.getExpiresAt()).toSeconds()), now,
                    challenge.getExpiresAt(), challenge.getResendAvailableAt(), "Enter your code when it arrives.", "CHALLENGE_INITIATED");
        }
        return genericDispatchResponse(
                challenge.getIssuanceId(),
				now,
				challenge.getExpiresAt(),
				challenge.getResendAvailableAt());
	}

	@Transactional(noRollbackFor = {InvalidAuthChallengeException.class, OtpAttemptLimitExceededException.class})
	public LoginOtpVerifyResult verifyLoginOtp(
			String challengeId,
			String rawIdentifier,
			String code,
			String currentSessionToken) {
		return verifyContactOtp(challengeId, rawIdentifier, code, currentSessionToken, false);
	}

	@Transactional(noRollbackFor = {InvalidAuthChallengeException.class, OtpAttemptLimitExceededException.class})
	public LoginOtpVerifyResult verifyRegistrationRecoveryOtp(String challengeId, String rawIdentifier,
			String code, String currentSessionToken) {
		return verifyContactOtp(challengeId, rawIdentifier, code, currentSessionToken, true);
	}

	private LoginOtpVerifyResult verifyContactOtp(String challengeId, String rawIdentifier, String code,
			String currentSessionToken, boolean recovery) {
		if (challengeId == null || challengeId.isBlank() || code == null || !code.matches("[0-9]{6}")) {
			throw new InvalidAuthChallengeException();
		}
		String trimmed = rawIdentifier == null ? "" : rawIdentifier.trim();
		boolean isEmail = trimmed.contains("@");
		String normalizedDestination;
		try {
			normalizedDestination = normalizeIdentifier(trimmed, isEmail);
		} catch (InvalidMobileNumberException
				| com.zaylope.registration.common.exception.InvalidRegistrationInputException ex) {
			throw new InvalidAuthChallengeException();
		}

		ApplicantVerificationChannel channel = isEmail
				? ApplicantVerificationChannel.EMAIL
				: ApplicantVerificationChannel.MOBILE;
		abuseGuard.check(0L, channel, abuseDestination(normalizedDestination, isEmail), clientIp(), false);
		if (recovery && channel != ApplicantVerificationChannel.EMAIL) throw new InvalidAuthChallengeException();

		ApplicantAuthChallenge challenge = challengeRepository.findByIssuanceIdForUpdate(challengeId.trim())
				.orElseThrow(InvalidAuthChallengeException::new);

		if (recovery
				? challenge.getPurpose() != ApplicantAuthChallenge.Purpose.DRAFT_RECOVERY
						&& challenge.getPurpose() != ApplicantAuthChallenge.Purpose.LEGACY_EMAIL_RECOVERY
				: challenge.getPurpose() != ApplicantAuthChallenge.Purpose.LOGIN) {
			throw new InvalidAuthChallengeException();
		}

		if (challenge.getChannel() != channel) {
			throw new InvalidAuthChallengeException();
		}

		Applicant applicant = applicantRepository.findByIdForUpdate(challenge.getApplicant().getApplicantId())
				.orElseThrow(InvalidAuthChallengeException::new);
		if (isAccountRestricted(applicant)) {
			challenge.invalidate(clock.instant());
			challengeRepository.saveAndFlush(challenge);
			throw new InvalidAuthChallengeException();
		}
		if (!matchesContact(applicant, normalizedDestination, isEmail)) {
			throw new InvalidAuthChallengeException();
		}

		String boundDestination = challengeDestination(applicant, normalizedDestination, isEmail);
		String expectedDestHash = otpHasher.fingerprint("DESTINATION:" + channel + ":" + boundDestination);
		if (!expectedDestHash.equals(challenge.getDestinationHash())) {
			throw new InvalidAuthChallengeException();
		}

		Instant now = clock.instant();
		validateAndConsumeChallenge(challenge, applicant.getApplicantId(), channel, boundDestination, code, now);

		boolean draftRecovered = false;
		ApplicantSessionScope scope;
		if (challenge.getPurpose() == ApplicantAuthChallenge.Purpose.DRAFT_RECOVERY) {
			if (!applicant.isUnverifiedUncommittedDraft() || applicant.getActiveEmailKey() == null
					|| applicant.isPendingEmailExpiredAt(now)) {
				throw new InvalidAuthChallengeException();
			}
			// Establish verified ownership ONLY for the email proven by OTP; release unverified mobile reservation
			applicant.markEmailVerified(now, Applicant.EMAIL_METHOD_OTP);
			applicantRepository.saveAndFlush(applicant);
			scope = ApplicantSessionScope.AUTHENTICATED;
			draftRecovered = true;
		} else {
			if (challenge.getPurpose() == ApplicantAuthChallenge.Purpose.LEGACY_EMAIL_RECOVERY) {
				if (channel != ApplicantVerificationChannel.EMAIL
						|| !Applicant.EMAIL_METHOD_LEGACY_UNKNOWN.equals(applicant.getEmailVerificationMethod())) {
					throw new InvalidAuthChallengeException();
				}
				applicant.markEmailVerified(now, Applicant.EMAIL_METHOD_OTP);
				applicantRepository.saveAndFlush(applicant);
			}
			if (channel == ApplicantVerificationChannel.MOBILE && !applicant.isMobileVerified()) {
				throw new InvalidAuthChallengeException();
			}
			if (channel == ApplicantVerificationChannel.EMAIL) {
				if (!applicant.isTrustedEmailVerified()) {
					throw new InvalidAuthChallengeException();
				}
			}
			scope = applicant.isContactVerified()
					? ApplicantSessionScope.AUTHENTICATED
					: ApplicantSessionScope.ONBOARDING;
		}

		String rotatedToken = sessionService.rotateSession(applicant, scope, currentSessionToken);
		return new LoginOtpVerifyResult(applicant, scope, rotatedToken, draftRecovered);
	}

	@Transactional(noRollbackFor = VerificationDeliveryUnavailableException.class)
	public LinkOtpDispatchResult sendLinkingOtp(long applicantId) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(InvalidAuthChallengeException::new);

		ApplicantVerificationChannel channel;
		String destination;
		if (applicant.isMobileVerified()) {
			if (!properties.mobileEnabled()) {
				throw new VerificationDeliveryUnavailableException();
			}
			channel = ApplicantVerificationChannel.MOBILE;
			destination = applicant.getMobileNumber();
		} else if (applicant.isTrustedEmailVerified()) {
			if (!properties.emailEnabled()) {
				throw new VerificationDeliveryUnavailableException();
			}
			channel = ApplicantVerificationChannel.EMAIL;
			destination = applicant.getEmail();
		} else {
			throw new IllegalStateException("Account has no verified contact available for linking.");
		}

		if (!properties.hasOtpHashKey()) {
			throw new VerificationDeliveryUnavailableException();
		}

		Instant now = clock.instant();
		abuseGuard.check(applicantId, channel, destination, clientIp(), true);
		ApplicantAuthChallenge challenge = issueChallenge(
				applicant,
				ApplicantAuthChallenge.Purpose.LINK_GOOGLE,
				channel,
				destination,
				now);

		String masked = channel == ApplicantVerificationChannel.MOBILE
				? ApplicantContactMasker.maskMobile(destination)
				: ApplicantContactMasker.maskEmail(destination);
		String deliveryMode = (channel == ApplicantVerificationChannel.MOBILE && mobileDelivery.isMock())
				|| (channel == ApplicantVerificationChannel.EMAIL && emailDelivery.isMock())
				? "MOCK" : "PROVIDER";
		return new LinkOtpDispatchResult(
				challenge.getIssuanceId(),
				channel,
				masked,
				properties.resendCooldown().toSeconds(),
				challenge.getExpiresAt(),
				challenge.getResendAvailableAt(),
				deliveryMode);
	}

	@Transactional(noRollbackFor = {InvalidAuthChallengeException.class, OtpAttemptLimitExceededException.class})
	public void verifyLinkingOtp(long applicantId, String code) {
		if (code == null || !code.matches("[0-9]{6}")) {
			throw new InvalidAuthChallengeException();
		}

		ApplicantAuthChallenge challenge = challengeRepository
				.findByApplicantAndPurposeForUpdate(applicantId, ApplicantAuthChallenge.Purpose.LINK_GOOGLE)
				.orElseThrow(InvalidAuthChallengeException::new);

		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(InvalidAuthChallengeException::new);

		String destination = challenge.getChannel() == ApplicantVerificationChannel.MOBILE
				? applicant.getMobileNumber()
				: applicant.getEmail();

		abuseGuard.check(applicantId, challenge.getChannel(), destination, clientIp(), false);

		String expectedDestHash = otpHasher.fingerprint("DESTINATION:" + challenge.getChannel() + ":" + destination);
		if (!expectedDestHash.equals(challenge.getDestinationHash())) {
			throw new InvalidAuthChallengeException();
		}

		validateAndConsumeChallenge(
				challenge,
				applicantId,
				challenge.getChannel(),
				destination,
				code,
				clock.instant());
	}

	private ApplicantAuthChallenge issueChallenge(
			Applicant applicant,
			ApplicantAuthChallenge.Purpose purpose,
			ApplicantVerificationChannel channel,
			String destination,
			Instant now) {
		long applicantId = applicant.getApplicantId();
		ApplicantAuthChallenge challenge = challengeRepository
				.findByApplicantAndPurposeForUpdate(applicantId, purpose)
				.orElse(null);

		String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(OTP_BOUND));
		String issuanceId = UUID.randomUUID().toString();
		String destHash = otpHasher.fingerprint("DESTINATION:" + channel + ":" + destination);
		String codeHash = hashAuthCode(purpose, issuanceId, applicantId, channel, destination, code);

		if (challenge == null) {
			challenge = new ApplicantAuthChallenge(
					applicant,
					purpose,
					channel,
					destHash,
					codeHash,
					issuanceId,
					now,
					now.plus(properties.otpLifetime()),
					now.plus(properties.resendCooldown()));
		} else {
			challenge.reissue(
					channel,
					destHash,
					codeHash,
					issuanceId,
					now,
					now.plus(properties.otpLifetime()),
					now.plus(properties.resendCooldown()),
					properties.sendWindow(),
					properties.maxSendsPerWindow());
		}

		challengeRepository.saveAndFlush(challenge);
		eventPublisher.publishEvent(new LoginOtpDispatchEvent(issuanceId, channel, destination, code));
		return challenge;
	}

	private void validateAndConsumeChallenge(
			ApplicantAuthChallenge challenge,
			long applicantId,
			ApplicantVerificationChannel channel,
			String destination,
			String code,
			Instant now) {
		if (!"ACTIVE".equals(challenge.getStatus()) || challenge.isConsumed()) {
			throw new InvalidAuthChallengeException();
		}
		if (challenge.hasExceededAttempts(properties.maxAttempts())) {
			throw new OtpAttemptLimitExceededException();
		}
		if (challenge.isExpiredAt(now)) {
			throw new InvalidAuthChallengeException();
		}
		String expectedHash = hashAuthCode(
				challenge.getPurpose(),
				challenge.getIssuanceId(),
				applicantId,
				channel,
				destination,
				code);
		if (!java.security.MessageDigest.isEqual(
				challenge.getCodeHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
				expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
			challenge.recordFailedAttempt(properties.maxAttempts());
			challengeRepository.saveAndFlush(challenge);
			if (challenge.hasExceededAttempts(properties.maxAttempts())) {
				throw new OtpAttemptLimitExceededException();
			}
			throw new InvalidAuthChallengeException();
		}
		challenge.consume(now);
		challengeRepository.saveAndFlush(challenge);
	}

	private String hashAuthCode(
			ApplicantAuthChallenge.Purpose purpose,
			String issuanceId,
			long applicantId,
			ApplicantVerificationChannel channel,
			String destination,
			String code) {
		return otpHasher.fingerprint("AUTH:" + purpose.name() + "\u0000" + issuanceId + "\u0000"
				+ applicantId + "\u0000" + channel.name() + "\u0000" + destination + "\u0000" + code);
	}

	private Optional<ResolvedLoginTarget> resolveLoginTarget(String normalized, boolean isEmail, Instant now, boolean recovery) {
		if (!isEmail) {
			if (recovery) return Optional.empty();
			// Unverified mobile numbers NEVER authenticate or receive login OTPs
			return applicantRepository.findByMobileNumberAndMobileVerifiedTrue(normalized)
					.filter(a -> a.isMobileVerified() && matchesContact(a, normalized, false))
					.map(a -> new ResolvedLoginTarget(a, ApplicantAuthChallenge.Purpose.LOGIN));
		}

		Optional<Applicant> verifiedByEmail = applicantRepository.findByEmailAndEmailVerifiedAtIsNotNull(normalized);
		if (verifiedByEmail.isEmpty()) {
			verifiedByEmail = applicantRepository.findLegacyVerifiedByEmailIgnoreCase(normalized);
		}
		verifiedByEmail = verifiedByEmail.filter(a -> a.isEmailVerified()
				&& (recovery ? Applicant.EMAIL_METHOD_LEGACY_UNKNOWN.equals(a.getEmailVerificationMethod())
						: a.isTrustedEmailVerified())
				&& matchesContact(a, normalized, true));
		if (verifiedByEmail.isPresent()) {
			return Optional.of(new ResolvedLoginTarget(verifiedByEmail.get(), recovery
					? ApplicantAuthChallenge.Purpose.LEGACY_EMAIL_RECOVERY : ApplicantAuthChallenge.Purpose.LOGIN));
		}
		if (!recovery) return Optional.empty();

		Optional<Applicant> activeDraft = applicantRepository.findByActiveEmailKey(normalized);
		if (activeDraft.isEmpty()) {
			activeDraft = applicantRepository.findLegacyActiveEmailKeyForUpdate(normalized);
		}
		if (activeDraft.isPresent()) {
			Applicant draft = activeDraft.get();
			if (matchesContact(draft, normalized, true)
					&& draft.isUnverifiedUncommittedDraft() && !draft.isPendingEmailExpiredAt(now)) {
				return Optional.of(new ResolvedLoginTarget(draft, ApplicantAuthChallenge.Purpose.DRAFT_RECOVERY));
			}
		}

		return Optional.empty();
	}

	private boolean isAccountRestricted(Applicant applicant) {
		Long applicantId = applicant.getApplicantId();
		if (applicantId == null) {
			return true;
		}
		return activationPolicy.isAccountRestricted(
				applicant,
				membershipRepository.findByApplicantId(applicantId));
	}

    private LoginOtpRequestResult explicitResult(String outcome, Instant now) {
        return new LoginOtpRequestResult(false, null, 0, 0, now, null, null, null, outcome);
    }
	private LoginOtpRequestResult genericDispatchResponse(String challengeId, Instant now) {
		return genericDispatchResponse(
				challengeId,
				now,
				now.plus(properties.otpLifetime()),
				now.plus(properties.resendCooldown()));
	}

	private LoginOtpRequestResult genericDispatchResponse(
			String challengeId,
			Instant now,
			Instant expiresAt,
			Instant resendAvailableAt) {
		return new LoginOtpRequestResult(
				true,
				challengeId,
				Math.max(0, Duration.between(now, resendAvailableAt).toSeconds()),
				Math.max(0, Duration.between(now, expiresAt).toSeconds()),
				now,
				expiresAt,
				resendAvailableAt,
				"If your details match an eligible account, you'll receive a 6-digit verification code.");
	}

	private static String normalizeIdentifier(String trimmed, boolean isEmail) {
		if (isEmail) {
			String normalized = com.zaylope.registration.common.validation.ApplicantInputValidator
					.normalizeEmail(trimmed);
			if (normalized == null) {
				throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
						"identifier",
						com.zaylope.registration.common.validation.ApplicantInputValidator.LOGIN_IDENTIFIER_MESSAGE);
			}
			return normalized;
		}
		return IndianMobileNumber.normalize(trimmed);
	}

	private static String clientIp() {
		return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
				? attrs.getRequest().getRemoteAddr()
				: "internal";
	}

	private static String abuseDestination(String normalizedDestination, boolean isEmail) {
		// Domain normalization already happened; preserve distinct case-sensitive local-part identities.
		return normalizedDestination;
	}

	private static String challengeDestination(Applicant applicant, String normalizedDestination, boolean isEmail) {
		return isEmail && !applicant.isEmailCaseSensitive()
				? applicant.getEmail()
				: normalizedDestination;
	}

	private static boolean matchesContact(Applicant applicant, String normalized, boolean isEmail) {
		if (!isEmail) {
			return normalized.equals(applicant.getMobileNumber());
		}
		return applicant.isEmailCaseSensitive()
				? normalized.equals(applicant.getEmail())
				: normalized.equalsIgnoreCase(applicant.getEmail());
	}

	private record ResolvedLoginTarget(Applicant applicant, ApplicantAuthChallenge.Purpose purpose) {}
}
