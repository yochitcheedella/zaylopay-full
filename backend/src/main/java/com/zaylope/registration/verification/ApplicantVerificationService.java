package com.zaylope.registration.verification;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.common.exception.MobileNumberAlreadyUsedException;
import com.zaylope.registration.common.exception.RegistrationAlreadySubmittedException;
import com.zaylope.registration.config.RegistrationProperties;
import com.zaylope.registration.config.VerificationProperties;
import com.zaylope.registration.registration.ApplicantRegistrationLock;
import com.zaylope.registration.registration.ApplicantRegistrationLockRepository;
import com.zaylope.registration.registration.IndianMobileNumber;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ApplicantVerificationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApplicantVerificationService.class);
	private static final int OTP_BOUND = 1_000_000;
	private static final int MAX_MOBILE_CHANGES_PER_WINDOW = 3;
	private static final Duration MOBILE_CHANGE_COOLDOWN = Duration.ofMinutes(1);
	private static final Duration MOBILE_CHANGE_WINDOW = Duration.ofHours(1);

	private final ApplicantRepository applicantRepository;
	private final ApplicantVerificationCodeRepository codeRepository;
	private final VerificationProperties properties;
	private final MobileOtpDelivery mobileDelivery;
	private final EmailOtpDelivery emailDelivery;
	private final ApplicantOtpHasher otpHasher;
	private final Clock clock;
	private final OtpAbuseGuard abuseGuard;
	private final ApplicantRegistrationLockRepository lockRepository;
	private final RegistrationProperties registrationProperties;
	private final SecureRandom secureRandom = new SecureRandom();
	private final ConcurrentMap<Long, MobileChangeWindow> mobileChangeWindows = new ConcurrentHashMap<>();
	private com.zaylope.registration.common.validation.EmailContactPolicy emailPolicy;
	private com.zaylope.registration.common.validation.EmailDomainValidator emailDomains;
	private org.springframework.transaction.support.TransactionTemplate contactTransactions;
	private com.zaylope.registration.auth.ApplicantAuthChallengeRepository authChallenges;
	private org.springframework.context.ApplicationEventPublisher contactEvents;

	@Autowired
	public void configureContactValidation(com.zaylope.registration.common.validation.EmailContactPolicy policy,
			com.zaylope.registration.common.validation.EmailDomainValidator domains,
			org.springframework.transaction.PlatformTransactionManager transactionManager,
			com.zaylope.registration.auth.ApplicantAuthChallengeRepository authChallenges,
			org.springframework.context.ApplicationEventPublisher events) {
		emailPolicy = policy;
		emailDomains = domains;
		contactTransactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
		this.authChallenges = authChallenges;
		contactEvents = events;
	}

	@Autowired
	public ApplicantVerificationService(
			ApplicantRepository applicantRepository,
			ApplicantVerificationCodeRepository codeRepository,
			VerificationProperties properties,
			MobileOtpDelivery mobileDelivery,
			EmailOtpDelivery emailDelivery,
			ApplicantOtpHasher otpHasher,
			Clock clock,
			OtpAbuseGuard abuseGuard,
			ApplicantRegistrationLockRepository lockRepository,
			RegistrationProperties registrationProperties) {
		this.applicantRepository = applicantRepository;
		this.codeRepository = codeRepository;
		this.properties = properties;
		this.mobileDelivery = mobileDelivery;
		this.emailDelivery = emailDelivery;
		this.otpHasher = otpHasher;
		this.clock = clock;
		this.abuseGuard = abuseGuard;
		this.lockRepository = lockRepository;
		this.registrationProperties = registrationProperties != null
				? registrationProperties
				: new RegistrationProperties(Duration.ofMinutes(30));
	}

	public ApplicantVerificationService(
			ApplicantRepository applicantRepository,
			ApplicantVerificationCodeRepository codeRepository,
			VerificationProperties properties,
			MobileOtpDelivery mobileDelivery,
			EmailOtpDelivery emailDelivery,
			ApplicantOtpHasher otpHasher,
			Clock clock, OtpAbuseGuard abuseGuard) {
		this(applicantRepository, codeRepository, properties, mobileDelivery, emailDelivery, otpHasher, clock, abuseGuard,
				null, new RegistrationProperties(Duration.ofMinutes(30)));
	}

	@Transactional(noRollbackFor = VerificationDeliveryUnavailableException.class)
	public OtpDispatchResponse sendMobileCode(long applicantId) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		if (applicant.isMobileVerified()) {
			throw new ApplicantAlreadyVerifiedException(ApplicantVerificationChannel.MOBILE);
		}
		requireDraft(applicant);
		requireDeliveryEnabled(properties.mobileEnabled());
		return issueCode(applicant, applicantId, ApplicantVerificationChannel.MOBILE);
	}

	@Transactional
	public MobileNumberChangeResponse changeMobileNumber(long applicantId, String requestedMobileNumber) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		requireDraft(applicant);
		String mobileNumber = IndianMobileNumber.normalize(requestedMobileNumber);
		if (applicant.getMobileNumber().equals(mobileNumber)) {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
					"mobileNumber", "Enter a different mobile number.");
		}
		if (applicant.isMobileVerified()) throw new ContactReauthenticationRequiredException();

		Instant now = clock.instant();
		enforceMobileChangeLimit(applicantId, now);
		abuseGuard.changeContact(applicantId, ApplicantVerificationChannel.MOBILE, applicant.getMobileNumber(), clientIp());
		if (applicantRepository.existsByMobileNumberAndMobileVerifiedTrue(mobileNumber)) {
			throw new MobileNumberAlreadyUsedException();
		}
		if (applicantRepository.existsByPendingMobileKeyAndApplicantIdNot(mobileNumber, applicantId)) {
			Optional<Applicant> holder = applicantRepository.findByPendingMobileKeyForUpdate(mobileNumber);
			if (holder.isPresent()
					&& holder.get().isUnverifiedUncommittedDraft()
					&& holder.get().isPendingMobileExpiredAt(now)) {
				holder.get().releasePendingMobileReservation();
				applicantRepository.saveAndFlush(holder.get());
			} else {
				throw new MobileNumberAlreadyUsedException();
			}
		}

		Instant expiresAt = now.plus(registrationProperties.pendingReservationTtl());
		String previousMobileNumber = applicant.getMobileNumber();
		applicant.changeMobileNumber(mobileNumber, expiresAt);
		invalidateAuthenticationChallenges(applicantId, ApplicantVerificationChannel.MOBILE, now);
		if (lockRepository != null) {
			ApplicantRegistrationLock lock = lockRepository.findByMobileNumberForUpdate(mobileNumber)
					.orElseGet(() -> lockRepository.saveAndFlush(new ApplicantRegistrationLock(mobileNumber)));
			lock.assign(applicantId, expiresAt);
			lockRepository.saveAndFlush(lock);
		}
		codeRepository.findByApplicantAndChannelForUpdate(
				applicantId, ApplicantVerificationChannel.MOBILE)
				.ifPresent(challenge -> {
					challenge.invalidate(now);
					codeRepository.saveAndFlush(challenge);
				});
		auditMobileNumberChange(applicantId, previousMobileNumber, mobileNumber, now);
		return new MobileNumberChangeResponse(
				ApplicantContactMasker.maskMobile(mobileNumber),
				applicant.isMobileVerified());
	}

	@Transactional(noRollbackFor = VerificationDeliveryUnavailableException.class)
	public OtpDispatchResponse sendEmailCode(long applicantId) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		if (applicant.isTrustedEmailVerified()) {
			throw new ApplicantAlreadyVerifiedException(ApplicantVerificationChannel.EMAIL);
		}
		requireDraft(applicant);
		requireDeliveryEnabled(properties.emailEnabled());
		return issueCode(applicant, applicantId, ApplicantVerificationChannel.EMAIL);
	}

	public ApplicantVerificationStatusResponse changeEmailAddress(long applicantId, String email) {
		if (emailPolicy != null) {
			// Network checks finish before acquiring any applicant/identity row locks.
			String normalized = emailPolicy.normalizeNewContact(email);
			emailDomains.validate(normalized);
			for (int attempt = 0; ; attempt++) {
				try {
					return contactTransactions.execute(status -> changeEmailAddressInTransaction(applicantId, normalized));
				} catch (org.springframework.dao.ConcurrencyFailureException conflict) {
					// Retry the whole rolled-back transaction, never an individual write with stale locks.
					if (attempt >= 1) throw new ContactUpdateConflictException();
				}
			}
		}
		return changeEmailAddressInTransaction(applicantId, email);
	}

	private ApplicantVerificationStatusResponse changeEmailAddressInTransaction(long applicantId, String email) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		requireDraft(applicant);
		String normalized = com.zaylope.registration.common.validation.ApplicantInputValidator.normalizeEmail(email);
		if (normalized == null) {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
					"email",
					com.zaylope.registration.common.validation.ApplicantInputValidator.EMAIL_MESSAGE);
		}
		Instant now = clock.instant();
		if (!applicant.getEmail().equals(normalized)) {
			if (applicant.isTrustedEmailVerified() || applicant.isEmailVerified())
				throw new ContactReauthenticationRequiredException();
			if (applicantRepository.existsByEmailAndEmailVerifiedAtIsNotNullAndApplicantIdNot(normalized, applicantId)) {
				throw new com.zaylope.registration.common.exception.DuplicateEmailException();
			}
			Optional<Applicant> activeHolder = applicantRepository.findByActiveEmailKeyForUpdate(normalized);
			if (activeHolder.isPresent() && !applicantIdEquals(activeHolder.get(), applicantId)) {
				Applicant other = activeHolder.get();
				if (other.isUnverifiedUncommittedDraft() && other.isPendingEmailExpiredAt(now)) {
					other.releasePendingEmailReservation();
					applicantRepository.saveAndFlush(other);
				} else {
					throw new com.zaylope.registration.common.exception.DuplicateEmailException();
				}
			}
			abuseGuard.changeContact(applicantId, ApplicantVerificationChannel.EMAIL, applicant.getEmail(), clientIp());
			applicant.changeEmailAddress(normalized, now.plus(registrationProperties.pendingReservationTtl()));
			invalidateAuthenticationChallenges(applicantId, ApplicantVerificationChannel.EMAIL, now);
			codeRepository.findByApplicantAndChannelForUpdate(applicantId, ApplicantVerificationChannel.EMAIL).ifPresent(code -> code.invalidate(now));
			applicantRepository.saveAndFlush(applicant);
		} else {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException("email", "Enter a different email address.");
		}
		return status(applicant);
	}

	private void invalidateAuthenticationChallenges(long applicantId, ApplicantVerificationChannel channel, Instant now) {
		if (authChallenges == null) return;
		for (var purpose : com.zaylope.registration.auth.ApplicantAuthChallenge.Purpose.values()) {
			authChallenges.findByApplicantAndPurposeForUpdate(applicantId, purpose)
					.filter(challenge -> challenge.getChannel() == channel)
					.ifPresent(challenge -> { challenge.invalidate(now); authChallenges.saveAndFlush(challenge); });
		}
	}

	private static boolean applicantIdEquals(Applicant other, long applicantId) {
		return other.getApplicantId() != null && other.getApplicantId() == applicantId;
	}

	@Transactional(noRollbackFor = {InvalidOtpException.class, OtpAttemptLimitExceededException.class})
	public ApplicantVerificationStatusResponse verifyMobileCode(long applicantId, String code) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		verifyCode(applicant, applicantId, ApplicantVerificationChannel.MOBILE, code);
		applicant.markMobileVerified(clock.instant());
		applicantRepository.saveAndFlush(applicant);
		if (lockRepository != null) {
			lockRepository.findByMobileNumberForUpdate(applicant.getMobileNumber()).ifPresent(lock -> {
				lock.markVerified(applicant.getApplicantId());
				lockRepository.saveAndFlush(lock);
			});
		}
		return status(applicant);
	}

	@Transactional(noRollbackFor = {InvalidOtpException.class, OtpAttemptLimitExceededException.class})
	public ApplicantVerificationStatusResponse verifyEmailCode(long applicantId, String code) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		verifyCode(applicant, applicantId, ApplicantVerificationChannel.EMAIL, code);
		applicant.markEmailVerified(clock.instant(), Applicant.EMAIL_METHOD_OTP);
		applicantRepository.saveAndFlush(applicant);
		return status(applicant);
	}

	@Transactional
	public VerificationStateResponse switchChannel(long applicantId, ApplicantVerificationChannel targetChannel) {
		Applicant applicant = findApplicantForUpdate(applicantId);
		requireDraft(applicant);
		Instant now = clock.instant();
		invalidateOtherActiveChallenge(applicantId, targetChannel, now);
		return buildVerificationState(applicant, applicantId, now);
	}

	private OtpDispatchResponse issueCode(
			Applicant applicant,
			long applicantId,
			ApplicantVerificationChannel channel) {
		if (!properties.hasOtpHashKey()) {
			throw new VerificationDeliveryUnavailableException();
		}
		Instant now = clock.instant();
		invalidateOtherActiveChallenge(applicantId, channel, now);
		String destination = destination(applicant, channel);
		abuseGuard.check(applicantId, channel, destination, clientIp(), true);
		String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(OTP_BOUND));
		ApplicantVerificationCode challenge = codeRepository
				.findByApplicantAndChannelForUpdate(applicantId, channel)
				.orElse(null);
		while (challenge != null && challenge.getIssuanceId() != null && otpHasher.matches(challenge.getCodeHash(), challenge.getIssuanceId(), applicantId, channel, destination, code)) {
			code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(OTP_BOUND));
		}
		if (challenge == null) {
			challenge = new ApplicantVerificationCode(
					applicant,
					channel,
					"pending",
					now,
					now.plus(properties.otpLifetime()),
					now.plus(properties.resendCooldown()),
					1);
		} else {
			challenge.resend(
					"pending",
					now,
					now.plus(properties.otpLifetime()),
					now.plus(properties.resendCooldown()),
					properties.sendWindow(),
					properties.maxSendsPerWindow());
		}
		challenge.bind(otpHasher.hash(challenge.getIssuanceId(), applicantId, channel, destination, code),
				otpHasher.fingerprint("DESTINATION:" + channel + ":" + destination));
		codeRepository.saveAndFlush(challenge);
		if (contactEvents != null) {
			contactEvents.publishEvent(new ContactOtpDispatchEvent(applicantId, channel, challenge.getIssuanceId(), destination, code));
			return new OtpDispatchResponse(mask(channel, destination), properties.resendCooldown().toSeconds(),
					now, challenge.getExpiresAt(), challenge.getResendAvailableAt(), deliveryMode());
		}
		// Compatibility for directly constructed unit fixtures; never deliver inside a transaction.
		if (TransactionSynchronizationManager.isActualTransactionActive())
			throw new IllegalStateException("Contact OTP delivery requires its after-commit publisher");
		try {
			if (channel == ApplicantVerificationChannel.MOBILE) mobileDelivery.sendCode(destination, code);
			else emailDelivery.sendCode(destination, code);
		} catch (RuntimeException failure) {
			challenge.deliveryFailed(now);
			codeRepository.saveAndFlush(challenge);
			throw new VerificationDeliveryUnavailableException();
		}
		return new OtpDispatchResponse(mask(channel, destination), properties.resendCooldown().toSeconds(),
				now, challenge.getExpiresAt(), challenge.getResendAvailableAt(), deliveryMode());
	}

	private void verifyCode(
			Applicant applicant,
			long applicantId,
			ApplicantVerificationChannel channel,
			String code) {
		requireDraft(applicant);
		if (code == null || !code.matches("[0-9]{6}")) throw new InvalidOtpException();
		String destination = destination(applicant, channel);
		abuseGuard.check(applicantId, channel, destination, clientIp(), false);
		ApplicantVerificationCode challenge = codeRepository
				.findByApplicantAndChannelForUpdate(applicantId, channel)
				.orElseThrow(OtpChallengeNotFoundException::new);
		if ("DELIVERY_FAILED".equals(challenge.getStatus())) throw new OtpChallengeNotFoundException();
		if (challenge.isConsumed()) {
			throw new OtpAlreadyUsedException();
		}
		if (challenge.hasExceededAttempts(properties.maxAttempts())) throw new OtpAttemptLimitExceededException();
		if (!challenge.isActiveFor(otpHasher.fingerprint("DESTINATION:" + channel + ":" + destination))) {
			throw new OtpChallengeNotFoundException();
		}
		if (channel == ApplicantVerificationChannel.MOBILE && applicant.isMobileVerified()
				|| channel == ApplicantVerificationChannel.EMAIL && applicant.isTrustedEmailVerified()) {
			throw new ApplicantAlreadyVerifiedException(channel);
		}
		Instant now = clock.instant();
		if (challenge.isExpiredAt(now)) {
			throw new OtpExpiredException();
		}
		if (challenge.hasExceededAttempts(properties.maxAttempts())) {
			throw new OtpAttemptLimitExceededException();
		}
		if (!otpHasher.matches(challenge.getCodeHash(), challenge.getIssuanceId(), applicantId, channel, destination, code)) {
			challenge.recordFailedAttempt();
			if (challenge.hasExceededAttempts(properties.maxAttempts())) challenge.lock();
			codeRepository.saveAndFlush(challenge);
			if (challenge.hasExceededAttempts(properties.maxAttempts())) {
				throw new OtpAttemptLimitExceededException();
			}
			throw new InvalidOtpException();
		}
		challenge.consume(now);
		codeRepository.saveAndFlush(challenge);
	}

	private Applicant findApplicantForUpdate(long applicantId) {
		return applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);
	}

	@Transactional(readOnly = true)
	public VerificationStateResponse verificationState(long applicantId) {
		Applicant applicant = applicantRepository.findById(applicantId).orElseThrow(ApplicantNotFoundException::new);
		Instant now = clock.instant();
		return buildVerificationState(applicant, applicantId, now);
	}

	private VerificationStateResponse buildVerificationState(Applicant applicant, long applicantId, Instant now) {
		return new VerificationStateResponse(now, deliveryMode(), channelState(applicant, applicantId, ApplicantVerificationChannel.MOBILE),
				channelState(applicant, applicantId, ApplicantVerificationChannel.EMAIL), applicant.isContactVerified());
	}

	private void invalidateOtherActiveChallenge(long applicantId, ApplicantVerificationChannel selectedChannel, Instant now) {
		ApplicantVerificationChannel otherChannel = selectedChannel == ApplicantVerificationChannel.MOBILE
				? ApplicantVerificationChannel.EMAIL
				: ApplicantVerificationChannel.MOBILE;
		codeRepository.findByApplicantAndChannelForUpdate(applicantId, otherChannel)
				.filter(challenge -> !challenge.isConsumed())
				.ifPresent(challenge -> {
					challenge.invalidate(now);
					codeRepository.saveAndFlush(challenge);
				});
	}
	private VerificationStateResponse.ChannelState channelState(Applicant applicant, long applicantId, ApplicantVerificationChannel channel) {
		var challenge = codeRepository.findByApplicantApplicantIdAndChannel(applicantId, channel).orElse(null);
		boolean active = challenge != null && properties.hasOtpHashKey() && challenge.isActiveFor(otpHasher.fingerprint("DESTINATION:" + channel + ":" + destination(applicant, channel)));
		boolean verified = channel == ApplicantVerificationChannel.MOBILE
				? applicant.isMobileVerified()
				: applicant.isTrustedEmailVerified();
		return new VerificationStateResponse.ChannelState(mask(channel, destination(applicant, channel)),
				verified,
				channel == ApplicantVerificationChannel.MOBILE ? properties.mobileEnabled() : properties.emailEnabled(),
				active ? challenge.getExpiresAt() : null, challenge == null ? null : challenge.getResendAvailableAt(),
				challenge == null ? "NOT_SENT" : "DELIVERY_FAILED".equals(challenge.getStatus()) ? "DELIVERY_FAILED"
						: challenge.hasExceededAttempts(properties.maxAttempts()) ? "LOCKED" : active && challenge.isExpiredAt(clock.instant()) ? "EXPIRED" : active ? "ACTIVE" : "NOT_ACTIVE");
	}
	private String deliveryMode() {
		return mobileDelivery.isMock() || emailDelivery.isMock() ? "MOCK" : "PROVIDER";
	}
	private static String destination(Applicant applicant, ApplicantVerificationChannel channel) { return channel == ApplicantVerificationChannel.MOBILE ? applicant.getMobileNumber() : applicant.getEmail(); }
	private static String mask(ApplicantVerificationChannel channel, String destination) { return channel == ApplicantVerificationChannel.MOBILE ? ApplicantContactMasker.maskMobile(destination) : ApplicantContactMasker.maskEmail(destination); }
	private static String clientIp() {
		return org.springframework.web.context.request.RequestContextHolder.getRequestAttributes() instanceof org.springframework.web.context.request.ServletRequestAttributes attrs ? attrs.getRequest().getRemoteAddr() : "internal";
	}

	private void enforceMobileChangeLimit(long applicantId, Instant now) {
		mobileChangeWindows.entrySet().removeIf(entry ->
				!now.isBefore(entry.getValue().windowStartedAt().plus(MOBILE_CHANGE_WINDOW)));
		mobileChangeWindows.compute(applicantId, (id, current) -> {
			MobileChangeWindow active = current;
			if (active == null || !now.isBefore(active.windowStartedAt().plus(MOBILE_CHANGE_WINDOW))) {
				active = new MobileChangeWindow(now, null, 0);
			}
			if (active.lastChangedAt() != null
					&& now.isBefore(active.lastChangedAt().plus(MOBILE_CHANGE_COOLDOWN))) {
				throw new MobileNumberChangeRateLimitException(
						retryAfter(now, active.lastChangedAt().plus(MOBILE_CHANGE_COOLDOWN)));
			}
			if (active.changeCount() >= MAX_MOBILE_CHANGES_PER_WINDOW) {
				throw new MobileNumberChangeRateLimitException(
						retryAfter(now, active.windowStartedAt().plus(MOBILE_CHANGE_WINDOW)));
			}
			return new MobileChangeWindow(active.windowStartedAt(), now, active.changeCount() + 1);
		});
	}

	private void auditMobileNumberChange(
			long applicantId,
			String previousMobileNumber,
			String newMobileNumber,
			Instant occurredAt) {
		Runnable logAudit = () -> LOGGER.info(
				"Applicant mobile number changed: applicantId={}, previousMobile={}, newMobile={}, occurredAt={}",
				applicantId,
				ApplicantContactMasker.maskMobile(previousMobileNumber),
				ApplicantContactMasker.maskMobile(newMobileNumber),
				occurredAt);
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					logAudit.run();
				}
			});
		} else {
			logAudit.run();
		}
	}

	private static long retryAfter(Instant now, Instant retryAt) {
		return Math.max(1, Duration.between(now, retryAt).toSeconds());
	}

	private static void requireDraft(Applicant applicant) {
		if (applicant.getRegistrationSubmittedAt() != null) {
			throw new RegistrationAlreadySubmittedException();
		}
	}

	private static void requireDeliveryEnabled(boolean enabled) {
		if (!enabled) {
			throw new VerificationDeliveryUnavailableException();
		}
	}

	private static ApplicantVerificationStatusResponse status(Applicant applicant) {
		return new ApplicantVerificationStatusResponse(
				applicant.isMobileVerified(),
				applicant.isTrustedEmailVerified());
	}

	private record MobileChangeWindow(Instant windowStartedAt, Instant lastChangedAt, int changeCount) {
	}
}
