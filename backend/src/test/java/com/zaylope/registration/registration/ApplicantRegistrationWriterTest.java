package com.zaylope.registration.registration;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.DuplicateEmailException;
import com.zaylope.registration.common.exception.MobileAlreadyVerifiedException;
import com.zaylope.registration.common.exception.MobileNumberAlreadyUsedException;
import com.zaylope.registration.config.RegistrationProperties;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicantRegistrationWriterTest {

	private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

	@Mock
	private ApplicantRepository applicantRepository;

	@Mock
	private ApplicantRegistrationLockRepository lockRepository;

	@Mock
	private ApplicantSessionService applicantSessionService;

	private ApplicantRegistrationWriter writer;

	@BeforeEach
	void setUp() {
		writer = new ApplicantRegistrationWriter(
				applicantRepository,
				lockRepository,
				applicantSessionService,
				com.zaylope.registration.common.validation.EmailContactPolicyFixtures.policy(false, false),
				new RegistrationProperties(Duration.ofMinutes(30)),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void newRegistrationCreatesAnUnverifiedApplicantWithOnboardingSession() {
		ApplicantRegistrationLock lock = new ApplicantRegistrationLock("+919876543210");
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Sai Sudheer", "9876543210", "kishore.test@example.com");
		when(lockRepository.findByMobileNumberForUpdate("+919876543210")).thenReturn(Optional.of(lock));
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(false);
		when(applicantRepository.findByPendingMobileKeyForUpdate("+919876543210")).thenReturn(Optional.empty());
		when(applicantRepository.findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull("kishore.test@example.com")).thenReturn(Optional.empty());
		when(applicantRepository.findByActiveEmailKeyForUpdate("kishore.test@example.com")).thenReturn(Optional.empty());
		when(applicantRepository.saveAndFlush(any(Applicant.class))).thenAnswer(inv -> inv.getArgument(0));
		when(applicantSessionService.createFor(any(Applicant.class), eq(ApplicantSessionScope.ONBOARDING)))
				.thenReturn("session-token");

		ApplicantRegistrationResult result = writer.register(request);

		assertEquals("DRAFT", result.response().status());
		assertEquals("session-token", result.sessionToken());
		verify(applicantRepository).saveAndFlush(argThat(saved ->
				"Sai Sudheer".equals(saved.getFullName())
						&& "+919876543210".equals(saved.getMobileNumber())
						&& "kishore.test@example.com".equals(saved.getEmail())
						&& !saved.isMobileVerified()
						&& NOW.plus(Duration.ofMinutes(30)).equals(saved.getPendingMobileExpiresAt())));
	}

	@Test
	void activeUnexpiredPendingMobileRegistrationIsRejected() {
		ApplicantRegistrationLock lock = new ApplicantRegistrationLock("+919876543210");
		Applicant existing = new Applicant("Old Name", "+919876543210", "old@example.com");
		existing.preparePendingRegistration("+919876543210", NOW.plus(Duration.ofMinutes(10)));
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Updated Name", "9876543210", "updated@example.com");
		when(lockRepository.findByMobileNumberForUpdate("+919876543210")).thenReturn(Optional.of(lock));
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(false);
		when(applicantRepository.findByPendingMobileKeyForUpdate("+919876543210")).thenReturn(Optional.of(existing));

		assertThrows(MobileNumberAlreadyUsedException.class, () -> writer.register(request));
		verify(applicantSessionService, never()).createFor(any(), any());
	}

	@Test
	void expiredUnverifiedDraftReservationIsReleasedWithoutDeletingExistingApplicant() {
		ApplicantRegistrationLock lock = new ApplicantRegistrationLock("+919876543210");
		Applicant expiredDraft = new Applicant("Abandoned", "+919876543210", "newowner@example.com");
		expiredDraft.preparePendingRegistration("+919876543210", NOW.minus(Duration.ofMinutes(5)));

		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Legitimate Owner", "9876543210", "newowner@example.com");
		when(lockRepository.findByMobileNumberForUpdate("+919876543210")).thenReturn(Optional.of(lock));
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(false);
		when(applicantRepository.findByPendingMobileKeyForUpdate("+919876543210")).thenReturn(Optional.of(expiredDraft));
		when(applicantRepository.findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull("newowner@example.com")).thenReturn(Optional.empty());
		when(applicantRepository.findByActiveEmailKeyForUpdate("newowner@example.com")).thenReturn(Optional.of(expiredDraft));
		when(applicantRepository.saveAndFlush(any(Applicant.class))).thenAnswer(inv -> inv.getArgument(0));
		when(applicantSessionService.createFor(any(Applicant.class), eq(ApplicantSessionScope.ONBOARDING)))
				.thenReturn("fresh-token");

		ApplicantRegistrationResult result = writer.register(request);

		assertEquals("fresh-token", result.sessionToken());
		assertNull(expiredDraft.getPendingMobileKey());
		assertNull(expiredDraft.getActiveEmailKey());
		assertEquals("+919876543210", expiredDraft.getMobileNumber());
	}

	@Test
	void verifiedEmailIsRejectedWithoutCreatingApplicant() {
		ApplicantRegistrationLock lock = new ApplicantRegistrationLock("+919876543210");
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Sai Sudheer", "9876543210", "kishore.test@example.com");
		when(lockRepository.findByMobileNumberForUpdate("+919876543210")).thenReturn(Optional.of(lock));
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(false);
		when(applicantRepository.findByPendingMobileKeyForUpdate("+919876543210")).thenReturn(Optional.empty());
		when(applicantRepository.findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull("kishore.test@example.com"))
				.thenReturn(Optional.of(new Applicant("Existing", "+919111111111", "kishore.test@example.com")));

		assertThrows(DuplicateEmailException.class, () -> writer.register(request));
		verify(applicantSessionService, never()).createFor(any(), any());
	}

	@Test
	void registrationPreservesEmailLocalPartCaseAsIdentity() {
		ApplicantRegistrationLock lock = new ApplicantRegistrationLock("+919876543210");
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Sai Sudheer", "9876543210", "Kishore.Test+Work@EXAMPLE.COM");
		when(lockRepository.findByMobileNumberForUpdate("+919876543210")).thenReturn(Optional.of(lock));
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(false);
		when(applicantRepository.findByPendingMobileKeyForUpdate("+919876543210")).thenReturn(Optional.empty());
		when(applicantRepository.findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull("Kishore.Test+Work@example.com"))
				.thenReturn(Optional.empty());
		when(applicantRepository.findByActiveEmailKeyForUpdate("Kishore.Test+Work@example.com"))
				.thenReturn(Optional.empty());
		when(applicantRepository.saveAndFlush(any(Applicant.class))).thenAnswer(inv -> inv.getArgument(0));
		when(applicantSessionService.createFor(any(Applicant.class), eq(ApplicantSessionScope.ONBOARDING)))
				.thenReturn("session-token");

		writer.register(request);

		verify(applicantRepository).saveAndFlush(argThat(saved ->
				"Kishore.Test+Work@example.com".equals(saved.getEmail())
						&& saved.isEmailCaseSensitive()));
	}

	@Test
	void verifiedMobileRegistrationIsRejected() {
		ApplicantRegistrationLock lock = new ApplicantRegistrationLock("+919876543210");
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Rahul Kumar", "9876543210", "rahul@example.com");
		when(lockRepository.findByMobileNumberForUpdate("+919876543210")).thenReturn(Optional.of(lock));
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(true);

		assertThrows(MobileAlreadyVerifiedException.class, () -> writer.register(request));
	}
}
