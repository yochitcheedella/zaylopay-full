package com.zaylope.registration.registration;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.DuplicateEmailException;
import com.zaylope.registration.common.exception.MobileAlreadyVerifiedException;
import com.zaylope.registration.common.exception.MobileNumberAlreadyUsedException;
import com.zaylope.registration.common.validation.EmailContactPolicy;
import com.zaylope.registration.config.RegistrationProperties;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantRegistrationWriter {

	private static final String REGISTRATION_DRAFT = "DRAFT";
	private static final String REGISTRATION_SUCCESS_MESSAGE = "Registration draft created successfully";

	private final ApplicantRepository applicantRepository;
	private final ApplicantRegistrationLockRepository lockRepository;
	private final ApplicantSessionService applicantSessionService;
	private final EmailContactPolicy emailContactPolicy;
	private final RegistrationProperties registrationProperties;
	private final Clock clock;

	@Autowired
	public ApplicantRegistrationWriter(
			ApplicantRepository applicantRepository,
			ApplicantRegistrationLockRepository lockRepository,
			ApplicantSessionService applicantSessionService,
			EmailContactPolicy emailContactPolicy,
			RegistrationProperties registrationProperties,
			Clock clock) {
		this.applicantRepository = applicantRepository;
		this.lockRepository = lockRepository;
		this.applicantSessionService = applicantSessionService;
		this.emailContactPolicy = emailContactPolicy;
		this.registrationProperties = registrationProperties;
		this.clock = clock;
	}

	public ApplicantRegistrationWriter(
			ApplicantRepository applicantRepository,
			ApplicantRegistrationLockRepository lockRepository,
			ApplicantSessionService applicantSessionService,
			EmailContactPolicy emailContactPolicy) {
		this(applicantRepository,
				lockRepository,
				applicantSessionService,
				emailContactPolicy,
				new RegistrationProperties(java.time.Duration.ofMinutes(30)),
				Clock.systemUTC());
	}

	@Transactional
	public ApplicantRegistrationResult register(ApplicantRegistrationRequest request) {
		String fullName = com.zaylope.registration.common.validation.ApplicantInputValidator
				.normalizeFullName(request.fullName());
		if (fullName == null) {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
					"fullName",
					com.zaylope.registration.common.validation.ApplicantInputValidator.FULL_NAME_MESSAGE);
		}
		String mobileNumber = IndianMobileNumber.normalize(request.mobileNumber());
		String email = emailContactPolicy.normalizeNewContact(request.email());
		Instant now = clock.instant();
		Instant reservationExpiresAt = now.plus(registrationProperties.pendingReservationTtl());

		ApplicantRegistrationLock lock = lockRepository.findByMobileNumberForUpdate(mobileNumber)
				.orElseGet(() -> lockRepository.saveAndFlush(
						new ApplicantRegistrationLock(mobileNumber)));

		if (applicantRepository.existsByMobileNumberAndMobileVerifiedTrue(mobileNumber)) {
			throw new MobileAlreadyVerifiedException();
		}

		Optional<Applicant> pendingMobileHolder = applicantRepository.findByPendingMobileKeyForUpdate(mobileNumber);
		if (pendingMobileHolder.isPresent()) {
			Applicant existing = pendingMobileHolder.get();
			if (existing.isUnverifiedUncommittedDraft() && existing.isPendingMobileExpiredAt(now)) {
				existing.releasePendingMobileReservation();
				applicantRepository.saveAndFlush(existing);
			} else {
				throw new MobileNumberAlreadyUsedException();
			}
		}

		if (applicantRepository.findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull(email).isPresent()) {
			throw new DuplicateEmailException();
		}

		Optional<Applicant> activeEmailHolder = applicantRepository.findByActiveEmailKeyForUpdate(email);
		if (activeEmailHolder.isPresent()) {
			Applicant existing = activeEmailHolder.get();
			if (existing.isUnverifiedUncommittedDraft() && existing.isPendingEmailExpiredAt(now)) {
				existing.releasePendingEmailReservation();
				applicantRepository.saveAndFlush(existing);
			} else {
				throw new DuplicateEmailException();
			}
		}

		Applicant applicant = new Applicant(fullName, mobileNumber, email);
		applicant.preparePendingRegistration(mobileNumber, reservationExpiresAt);
		applicantRepository.saveAndFlush(applicant);

		lock.assign(applicant.getApplicantId(), reservationExpiresAt);
		lockRepository.saveAndFlush(lock);

		String sessionToken = applicantSessionService.createFor(applicant, ApplicantSessionScope.ONBOARDING);
		return new ApplicantRegistrationResult(
				new ApplicantRegistrationResponse(
						REGISTRATION_SUCCESS_MESSAGE,
						REGISTRATION_DRAFT,
						"APP-" + applicant.getApplicantId()),
				sessionToken);
	}
}
