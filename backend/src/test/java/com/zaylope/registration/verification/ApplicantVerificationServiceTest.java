package com.zaylope.registration.verification;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.MobileNumberAlreadyUsedException;
import com.zaylope.registration.config.VerificationProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicantVerificationServiceTest {

	private static final long APPLICANT_ID = 7L;
	private static final Instant NOW = Instant.parse("2026-11-19T05:00:00Z");
	private static final String HASH_KEY = "local-test-key-with-at-least-32-bytes";

	@Mock
	private ApplicantRepository applicantRepository;

	@Mock
	private ApplicantVerificationCodeRepository codeRepository;

	@Mock
	private MobileOtpDelivery mobileDelivery;

	@Mock
	private EmailOtpDelivery emailDelivery;

	private final Map<ApplicantVerificationChannel, ApplicantVerificationCode> challenges =
			new EnumMap<>(ApplicantVerificationChannel.class);
	private Applicant applicant;
	private ApplicantOtpHasher hasher;
	private ApplicantVerificationService service;
	private String sentMobileCode;
	private String sentEmailCode;

	@BeforeEach
	void setUp() {
		VerificationProperties properties = properties(true, true);
		hasher = new ApplicantOtpHasher(properties);
		applicant = new Applicant("Test Applicant", "+919876543210", "test@example.com");
		service = new ApplicantVerificationService(
				applicantRepository,
				codeRepository,
				properties,
				mobileDelivery,
				emailDelivery,
				hasher,
				Clock.fixed(NOW, ZoneOffset.UTC), org.mockito.Mockito.mock(OtpAbuseGuard.class));
		when(applicantRepository.findByIdForUpdate(APPLICANT_ID)).thenReturn(Optional.of(applicant));
		lenient().when(codeRepository.findByApplicantAndChannelForUpdate(
				APPLICANT_ID, ApplicantVerificationChannel.MOBILE))
				.thenAnswer(invocation -> Optional.ofNullable(
						challenges.get(ApplicantVerificationChannel.MOBILE)));
		lenient().when(codeRepository.findByApplicantAndChannelForUpdate(
				APPLICANT_ID, ApplicantVerificationChannel.EMAIL))
				.thenAnswer(invocation -> Optional.ofNullable(
						challenges.get(ApplicantVerificationChannel.EMAIL)));
		lenient().when(codeRepository.saveAndFlush(any(ApplicantVerificationCode.class)))
				.thenAnswer(invocation -> {
					ApplicantVerificationCode challenge = invocation.getArgument(0);
					challenges.put(challenge.getChannel(), challenge);
					return challenge;
				});
		lenient().doAnswer(invocation -> {
			sentMobileCode = invocation.getArgument(1);
			return null;
		}).when(mobileDelivery).sendCode(any(String.class), any(String.class));
		lenient().doAnswer(invocation -> {
			sentEmailCode = invocation.getArgument(1);
			return null;
		}).when(emailDelivery).sendCode(any(String.class), any(String.class));
	}

	@Test
	void mobileOnlyVerificationSendsOnlyMobileAndLeavesEmailUnverified() {
		OtpDispatchResponse mobileResponse = service.sendMobileCode(APPLICANT_ID);

		assertEquals("+91 •••••• 3210", mobileResponse.maskedDestination());
		assertTrue(sentMobileCode.matches("[0-9]{6}"));
		assertNotEquals(sentMobileCode, challenges.get(ApplicantVerificationChannel.MOBILE).getCodeHash());
		assertFalse(mobileResponse.toString().contains(sentMobileCode));
		verify(emailDelivery, never()).sendCode(any(String.class), any(String.class));

		String incorrectCode = sentMobileCode.equals("000000") ? "000001" : "000000";
		assertThrows(InvalidOtpException.class, () -> service.verifyMobileCode(APPLICANT_ID, incorrectCode));
		ApplicantVerificationStatusResponse mobileStatus =
				service.verifyMobileCode(APPLICANT_ID, sentMobileCode);
		assertTrue(mobileStatus.mobileVerified());
		assertFalse(mobileStatus.emailVerified());
		assertTrue(mobileStatus.contactVerified());
		assertFalse(applicant.isEmailVerified());

		assertThrows(OtpAlreadyUsedException.class,
				() -> service.verifyMobileCode(APPLICANT_ID, sentMobileCode));
	}

	@Test
	void emailOnlyVerificationSendsOnlyEmailAndLeavesMobileUnverified() {
		OtpDispatchResponse emailResponse = service.sendEmailCode(APPLICANT_ID);

		assertEquals("t••••@example.com", emailResponse.maskedDestination());
		assertTrue(sentEmailCode.matches("[0-9]{6}"));
		assertNotEquals(sentEmailCode, challenges.get(ApplicantVerificationChannel.EMAIL).getCodeHash());
		verify(mobileDelivery, never()).sendCode(any(String.class), any(String.class));

		String incorrectCode = sentEmailCode.equals("000000") ? "000001" : "000000";
		assertThrows(InvalidOtpException.class, () -> service.verifyEmailCode(APPLICANT_ID, incorrectCode));
		ApplicantVerificationStatusResponse emailStatus =
				service.verifyEmailCode(APPLICANT_ID, sentEmailCode);
		assertFalse(emailStatus.mobileVerified());
		assertTrue(emailStatus.emailVerified());
		assertTrue(emailStatus.contactVerified());
		assertFalse(applicant.isMobileVerified());

		assertThrows(OtpAlreadyUsedException.class,
				() -> service.verifyEmailCode(APPLICANT_ID, sentEmailCode));
	}

	@Test
	void switchingChannelInvalidatesOutstandingChallengeOnPreviousChannel() {
		service.sendMobileCode(APPLICANT_ID);
		String oldMobileCode = sentMobileCode;
		lenient().when(applicantRepository.findById(APPLICANT_ID)).thenReturn(Optional.of(applicant));

		VerificationStateResponse switchedState = service.switchChannel(APPLICANT_ID, ApplicantVerificationChannel.EMAIL);
		assertFalse(switchedState.contactVerified());
		assertEquals("INVALIDATED", challenges.get(ApplicantVerificationChannel.MOBILE).getStatus());
		assertThrows(OtpAlreadyUsedException.class,
				() -> service.verifyMobileCode(APPLICANT_ID, oldMobileCode));

		service.sendEmailCode(APPLICANT_ID);
		ApplicantVerificationStatusResponse verified = service.verifyEmailCode(APPLICANT_ID, sentEmailCode);
		assertTrue(verified.emailVerified());
		assertFalse(verified.mobileVerified());
		assertTrue(verified.contactVerified());
	}

	@Test
	void enforcesResendCooldownAndMaximumVerificationAttempts() {
		service.sendMobileCode(APPLICANT_ID);
		assertThrows(OtpRateLimitException.class, () -> service.sendMobileCode(APPLICANT_ID));

		String incorrectCode = sentMobileCode.equals("000000") ? "000001" : "000000";
		for (int attempt = 1; attempt < 5; attempt++) {
			assertThrows(InvalidOtpException.class,
					() -> service.verifyMobileCode(APPLICANT_ID, incorrectCode));
		}
		assertThrows(OtpAttemptLimitExceededException.class,
				() -> service.verifyMobileCode(APPLICANT_ID, incorrectCode));
		assertThrows(OtpAttemptLimitExceededException.class,
				() -> service.verifyMobileCode(APPLICANT_ID, sentMobileCode));
		assertFalse(applicant.isMobileVerified());
	}

	@Test
	void rejectsExpiredCodesAndLeavesMobileUnverified() {
		ApplicantVerificationCode expired = new ApplicantVerificationCode(
				applicant,
				ApplicantVerificationChannel.MOBILE,
				"a".repeat(64),
				NOW.minus(Duration.ofMinutes(10)),
				NOW.minus(Duration.ofMinutes(5)),
				NOW.minus(Duration.ofMinutes(9)),
				1);
		expired.bind(expired.getCodeHash(), hasher.fingerprint("DESTINATION:MOBILE:" + applicant.getMobileNumber()));
		challenges.put(ApplicantVerificationChannel.MOBILE, expired);

		assertThrows(OtpExpiredException.class,
				() -> service.verifyMobileCode(APPLICANT_ID, "123456"));
		assertFalse(applicant.isMobileVerified());
	}

	@Test
	void changingUnverifiedMobileInvalidatesItsOldChallenge() {
		service.sendMobileCode(APPLICANT_ID);
		String previousCode = sentMobileCode;

		MobileNumberChangeResponse response =
				service.changeMobileNumber(APPLICANT_ID, "+919876543211");

		assertEquals("+91 •••••• 3211", response.maskedDestination());
		assertFalse(response.mobileVerified());
		assertEquals("+919876543211", applicant.getMobileNumber());
		assertFalse(applicant.isMobileVerified());
		assertTrue(challenges.get(ApplicantVerificationChannel.MOBILE).isConsumed());
		assertThrows(OtpAlreadyUsedException.class,
				() -> service.verifyMobileCode(APPLICANT_ID, previousCode));
	}

	@Test
	void verifiedContactsCannotBeReplacedByDraftCorrection() {
		applicant.markMobileVerified(NOW);
		assertThrows(ContactReauthenticationRequiredException.class,
				() -> service.changeMobileNumber(APPLICANT_ID, "+919876543211"));
		applicant.markEmailVerified(NOW, Applicant.EMAIL_METHOD_OTP);
		assertThrows(ContactReauthenticationRequiredException.class,
				() -> service.changeEmailAddress(APPLICANT_ID, "other@gmail.com"));
		assertEquals("test@example.com", applicant.getEmail());
		assertEquals("+919876543210", applicant.getMobileNumber());
	}

	@Test
	void unchangedContactsAreRejectedWithoutChangingOwnership() {
		assertThrows(com.zaylope.registration.common.exception.InvalidRegistrationInputException.class,
				() -> service.changeEmailAddress(APPLICANT_ID, applicant.getEmail()));
		assertThrows(com.zaylope.registration.common.exception.InvalidRegistrationInputException.class,
				() -> service.changeMobileNumber(APPLICANT_ID, applicant.getMobileNumber()));
	}

	@Test
	void changingEmailInvalidatesOldCodeAndNeverMarksNewAddressVerified() {
		service.sendEmailCode(APPLICANT_ID);
		String oldCode = sentEmailCode;
		service.changeEmailAddress(APPLICANT_ID, "new@gmail.com");
		assertFalse(applicant.isEmailVerified());
		assertTrue(challenges.get(ApplicantVerificationChannel.EMAIL).isConsumed());
		assertThrows(OtpAlreadyUsedException.class, () -> service.verifyEmailCode(APPLICANT_ID, oldCode));
	}

	@Test
	void rateLimitsRepeatedMobileNumberChanges() {
		service.changeMobileNumber(APPLICANT_ID, "+919876543211");

		assertThrows(
				MobileNumberChangeRateLimitException.class,
				() -> service.changeMobileNumber(APPLICANT_ID, "+919876543212"));
	}

	@Test
	void rejectsMobileNumberOwnedByAnotherApplicant() {
		when(applicantRepository.existsByMobileNumberAndMobileVerifiedTrue("+919876543211"))
				.thenReturn(true);

		assertThrows(
				MobileNumberAlreadyUsedException.class,
				() -> service.changeMobileNumber(APPLICANT_ID, "+919876543211"));
		assertEquals("+919876543210", applicant.getMobileNumber());
	}

	@Test
	void doesNotAttemptDeliveryOrPersistCodesWhenProviderIsDisabled() {
		VerificationProperties disabled = properties(false, false);
		ApplicantVerificationService disabledService = new ApplicantVerificationService(
				applicantRepository,
				codeRepository,
				disabled,
				mobileDelivery,
				emailDelivery,
				new ApplicantOtpHasher(disabled),
				Clock.fixed(NOW, ZoneOffset.UTC), org.mockito.Mockito.mock(OtpAbuseGuard.class));

		assertThrows(VerificationDeliveryUnavailableException.class,
				() -> disabledService.sendMobileCode(APPLICANT_ID));

		verify(mobileDelivery, never()).sendCode(any(String.class), any(String.class));
		verify(codeRepository, never()).saveAndFlush(any(ApplicantVerificationCode.class));
	}

	private static VerificationProperties properties(boolean mobileEnabled, boolean emailEnabled) {
		return new VerificationProperties(
				mobileEnabled,
				emailEnabled,
				HASH_KEY,
				Duration.ofMinutes(5),
				Duration.ofSeconds(60),
				Duration.ofHours(1),
				5,
				5);
	}
}

