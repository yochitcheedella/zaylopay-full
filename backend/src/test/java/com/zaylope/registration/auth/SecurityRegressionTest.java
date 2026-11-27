package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.affiliate.MembershipActivationPolicy;
import com.zaylope.registration.affiliate.ApplicantMembershipController;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantBankDetailsController;
import com.zaylope.registration.applicant.ApplicantBankDetailsRepository;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.applicant.BankEncryptionService;
import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.common.exception.InvalidAuthChallengeException;
import com.zaylope.registration.config.ApplicantSessionProperties;
import com.zaylope.registration.config.VerificationProperties;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantOtpHasher;
import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import com.zaylope.registration.verification.OtpAbuseGuard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityRegressionTest {

	private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

	@Test
	void unverifiedMobileCannotReceiveLoginOtpAndResponseDoesNotEnumerate() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		MobileOtpDelivery mobileDelivery = mock(MobileOtpDelivery.class);
		EmailOtpDelivery emailDelivery = mock(EmailOtpDelivery.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, eventPublisher, props, mobileDelivery, emailDelivery,
				new ApplicantOtpHasher(props), abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		// Even if an applicant exists with unverified mobile,
		// findByMobileNumberAndMobileVerifiedTrue returns empty
		when(applicantRepo.findByMobileNumberAndMobileVerifiedTrue("+919876543210")).thenReturn(Optional.empty());
		when(applicantRepo.findByMobileNumberAndMobileVerifiedTrue("+919876543299")).thenReturn(Optional.empty());

		var unverifiedResp = service.requestLoginOtp("9876543210");
		var unknownResp = service.requestLoginOtp("9876543299");

		assertEquals(unverifiedResp.accepted(), unknownResp.accepted());
		assertEquals(unverifiedResp.message(), unknownResp.message());
		assertEquals(unverifiedResp.resendAfterSeconds(), unknownResp.resendAfterSeconds());
		assertEquals(unverifiedResp.expiresInSeconds(), unknownResp.expiresInSeconds());
		assertEquals(unverifiedResp.serverTime(), unknownResp.serverTime());
		assertEquals(unverifiedResp.expiresAt(), unknownResp.expiresAt());
		assertEquals(unverifiedResp.resendAvailableAt(), unknownResp.resendAvailableAt());
		verify(mobileDelivery, never()).sendCode(anyString(), anyString());
		verify(challengeRepo, never()).saveAndFlush(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void restrictedAccountGetsGenericResponseAndNoLoginChallenge() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		MobileOtpDelivery mobileDelivery = mock(MobileOtpDelivery.class);
		EmailOtpDelivery emailDelivery = mock(EmailOtpDelivery.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		Applicant restricted = new Applicant("Restricted Member", "+919876543210", "restricted@example.com");
		ReflectionTestUtils.setField(restricted, "applicantId", 42L);
		restricted.markMobileVerified(NOW);
		restricted.markRejected("Policy restriction", NOW);
		when(applicantRepo.findByMobileNumberAndMobileVerifiedTrue("+919876543210"))
				.thenReturn(Optional.of(restricted));
		when(membershipRepo.findByApplicantId(42L)).thenReturn(Optional.empty());

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, eventPublisher, props, mobileDelivery, emailDelivery,
				new ApplicantOtpHasher(props), abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		var response = service.requestLoginOtp("9876543210");

		assertEquals(true, response.accepted());
		assertEquals("If your details match an eligible account, you'll receive a 6-digit verification code.",
				response.message());
		verify(mobileDelivery, never()).sendCode(anyString(), anyString());
		verify(challengeRepo, never()).saveAndFlush(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void eligibleCustomDomainEmailUsesGenericResponseAndPublishesOtpOnlyAfterCommit() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		EmailOtpDelivery emailDelivery = mock(EmailOtpDelivery.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		String email = "Login.User+Tag@custom-domain.dev";
		Applicant applicant = new Applicant("Login User", "+919876543210", email);
		ReflectionTestUtils.setField(applicant, "applicantId", 43L);
		applicant.markEmailVerified(NOW, Applicant.EMAIL_METHOD_OTP);
		Applicant legacyCaseVariant = new Applicant("Legacy User", "+919876543299", "login.user+tag@custom-domain.dev");
		ReflectionTestUtils.setField(legacyCaseVariant, "emailCaseSensitive", false);
		when(applicantRepo.findByEmailAndEmailVerifiedAtIsNotNull(email))
				.thenReturn(Optional.of(applicant));
		when(applicantRepo.findLegacyVerifiedByEmailIgnoreCase(email))
				.thenReturn(Optional.of(legacyCaseVariant));
		when(membershipRepo.findByApplicantId(43L)).thenReturn(Optional.empty());

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, eventPublisher, props, mock(MobileOtpDelivery.class), emailDelivery,
				new ApplicantOtpHasher(props), abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		var response = service.requestLoginOtp("Login.User+Tag@CUSTOM-DOMAIN.DEV");

		assertEquals(true, response.accepted());
		assertEquals("If your details match an eligible account, you'll receive a 6-digit verification code.",
				response.message());
		assertEquals(300, response.expiresInSeconds());
		assertEquals(60, response.resendAfterSeconds());
		verify(emailDelivery, never()).sendCode(anyString(), anyString());
		verify(applicantRepo, never()).findLegacyVerifiedByEmailIgnoreCase(email);
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		LoginOtpDispatchEvent event = (LoginOtpDispatchEvent) eventCaptor.getValue();
		assertEquals(response.challengeId(), event.issuanceId());
		assertEquals(ApplicantVerificationChannel.EMAIL, event.channel());
		assertEquals(email, event.destination());
		org.junit.jupiter.api.Assertions.assertTrue(event.code().matches("[0-9]{6}"));
	}

	@Test
	void eligibleVerifiedMobilePublishesOtpEventWithoutSynchronousDelivery() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		MobileOtpDelivery mobileDelivery = mock(MobileOtpDelivery.class);
		EmailOtpDelivery emailDelivery = mock(EmailOtpDelivery.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		Applicant applicant = new Applicant("Mobile Member", "+919876543210", "mobile@example.com");
		ReflectionTestUtils.setField(applicant, "applicantId", 45L);
		applicant.markMobileVerified(NOW);
		when(applicantRepo.findByMobileNumberAndMobileVerifiedTrue("+919876543210"))
				.thenReturn(Optional.of(applicant));
		when(membershipRepo.findByApplicantId(45L)).thenReturn(Optional.empty());

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, eventPublisher, props, mobileDelivery, emailDelivery,
				new ApplicantOtpHasher(props), abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		var response = service.requestLoginOtp("9876543210");

		assertEquals(true, response.accepted());
		verify(mobileDelivery, never()).sendCode(anyString(), anyString());
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		LoginOtpDispatchEvent event = (LoginOtpDispatchEvent) eventCaptor.getValue();
		assertEquals(response.challengeId(), event.issuanceId());
		assertEquals(ApplicantVerificationChannel.MOBILE, event.channel());
		assertEquals("+919876543210", event.destination());
		org.junit.jupiter.api.Assertions.assertTrue(event.code().matches("[0-9]{6}"));
	}

	@Test
	void unknownEmailGetsGenericResponseAndCreatesNoChallengeOrDeliveryEvent() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		EmailOtpDelivery emailDelivery = mock(EmailOtpDelivery.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, eventPublisher, props, mock(MobileOtpDelivery.class), emailDelivery,
				new ApplicantOtpHasher(props), abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		var response = service.requestLoginOtp("unknown.user@example.dev");

		assertEquals(true, response.accepted());
		assertEquals("If your details match an eligible account, you'll receive a 6-digit verification code.",
				response.message());
		verify(emailDelivery, never()).sendCode(anyString(), anyString());
		verify(challengeRepo, never()).saveAndFlush(any());
		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void emailLoginFallsBackToLegacyCaseInsensitiveRecordOnlyWhenNoExactMatchExists() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		EmailOtpDelivery emailDelivery = mock(EmailOtpDelivery.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		String canonicalEmail = "legacy.user@example.com";
		Applicant legacyApplicant = new Applicant("Legacy User", "+919876543210", canonicalEmail);
		ReflectionTestUtils.setField(legacyApplicant, "applicantId", 44L);
		ReflectionTestUtils.setField(legacyApplicant, "emailCaseSensitive", false);
		ReflectionTestUtils.setField(legacyApplicant, "email", canonicalEmail);
		legacyApplicant.markEmailVerified(NOW, Applicant.EMAIL_METHOD_OTP);
		when(applicantRepo.findByEmailAndEmailVerifiedAtIsNotNull("Legacy.User@example.com"))
				.thenReturn(Optional.empty());
		when(applicantRepo.findLegacyVerifiedByEmailIgnoreCase("Legacy.User@example.com"))
				.thenReturn(Optional.of(legacyApplicant));
		when(membershipRepo.findByApplicantId(44L)).thenReturn(Optional.empty());

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, eventPublisher, props, mock(MobileOtpDelivery.class), emailDelivery,
				new ApplicantOtpHasher(props), abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		service.requestLoginOtp("Legacy.User@EXAMPLE.COM");

		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		LoginOtpDispatchEvent event = (LoginOtpDispatchEvent) eventCaptor.getValue();
		assertEquals(canonicalEmail, event.destination());
	}

	@Test
	void linkGoogleChallengeCannotBeReplayedAgainstLoginVerify() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		ApplicantOtpHasher hasher = new ApplicantOtpHasher(props);

		Applicant applicant = new Applicant("Test", "+919876543210", "test@example.com");
		ReflectionTestUtils.setField(applicant, "applicantId", 42L);
		applicant.markMobileVerified(NOW);

		ApplicantAuthChallenge linkChallenge = new ApplicantAuthChallenge(
				applicant,
				ApplicantAuthChallenge.Purpose.LINK_GOOGLE,
				ApplicantVerificationChannel.MOBILE,
				hasher.fingerprint("DESTINATION:MOBILE:+919876543210"),
				"unused",
				"issuance-link-1",
				NOW,
				NOW.plusSeconds(300),
				NOW.plusSeconds(60));
		when(challengeRepo.findByIssuanceIdForUpdate("issuance-link-1")).thenReturn(Optional.of(linkChallenge));

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, mock(ApplicationEventPublisher.class), props,
				mock(MobileOtpDelivery.class), mock(EmailOtpDelivery.class),
				hasher, abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThrows(InvalidAuthChallengeException.class,
				() -> service.verifyLoginOtp("issuance-link-1", "9876543210", "123456", null));
	}

	@Test
	void bankDetailsEndpointRequiresVerifiedMobileNumber() throws Exception {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		ApplicantBankDetailsRepository bankRepo = mock(ApplicantBankDetailsRepository.class);
		Applicant emailOnly = new Applicant("Email Only", "+919876543210", "emailonly@gmail.com");
		emailOnly.markEmailVerified(NOW, Applicant.EMAIL_METHOD_GOOGLE);
		when(applicantRepo.findById(42L)).thenReturn(Optional.of(emailOnly));

		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
				new ApplicantBankDetailsController(bankRepo, applicantRepo, mock(BankEncryptionService.class)))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		mockMvc.perform(get("/api/v1/applicants/me/bank-details")
				.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("MOBILE_VERIFICATION_REQUIRED"));
	}

	@Test
	void membershipEndpointRejectsAccessWhenNotPaidSubmittedVerifiedAndActiveWithoutAdminApprovalRequirement()
			throws Exception {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);

		// Case 1: Applicant has legacy affiliateId set on applicants table, but no
		// payment or active membership -> denied!
		Applicant unactivated = new Applicant("Pending User", "+919876543210", "pending@example.com");
		ReflectionTestUtils.setField(unactivated, "affiliateId", "ZP-FAKE");
		when(applicantRepo.findById(42L)).thenReturn(Optional.of(unactivated));

		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
				new ApplicantMembershipController(membershipRepo, applicantRepo, "http://localhost:5173"))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		mockMvc.perform(get("/api/v1/applicants/me/membership")
				.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AFFILIATE_ACCESS_DENIED"));

		// Case 2: Verified + Submitted + Paid + Active Membership (WITH
		// approvalStatus=PENDING, NO admin approval needed) -> allowed!
		unactivated.markMobileVerified(NOW);
		unactivated.recordPrivacyConsent(NOW, "onboarding-v1");
		unactivated.markRegistrationSubmitted(NOW);
		unactivated.markPaymentPaid();
		AffiliateMembership activeMembership = new AffiliateMembership(42L, "ZP-261125-001", "ZPREF1", null, NOW,
				"LIFETIME");
		when(membershipRepo.findByApplicantId(42L)).thenReturn(Optional.of(activeMembership));

		mockMvc.perform(get("/api/v1/applicants/me/membership")
				.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.affiliateId").value("ZP-261125-001"))
				.andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
		assertEquals("PENDING", unactivated.getApprovalStatus());

		// Case 3: Rejected / Restricted account with AffiliateMembership -> denied!
		unactivated.markRejected("Suspended due to policy breach", NOW);
		mockMvc.perform(get("/api/v1/applicants/me/membership")
				.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("AFFILIATE_ACCESS_DENIED"));
	}

	@Test
	void loginVerifyRejectsChannelThatWasNotVerifiedForApplicant() {
		ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
		AffiliateMembershipRepository membershipRepo = mock(AffiliateMembershipRepository.class);
		ApplicantAuthChallengeRepository challengeRepo = mock(ApplicantAuthChallengeRepository.class);
		OtpAbuseGuard abuseGuard = mock(OtpAbuseGuard.class);
		ApplicantSessionService sessionService = mock(ApplicantSessionService.class);
		VerificationProperties props = new VerificationProperties(
				true, true, "test-otp-hash-key-with-minimum-32-bytes!",
				Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
		ApplicantOtpHasher hasher = new ApplicantOtpHasher(props);

		// Applicant has verified email ONLY; mobile is NOT verified
		Applicant applicant = new Applicant("Email Verified", "+919876543210", "only@gmail.com");
		ReflectionTestUtils.setField(applicant, "applicantId", 77L);
		applicant.markEmailVerified(NOW, Applicant.EMAIL_METHOD_GOOGLE);
		when(applicantRepo.findByIdForUpdate(77L)).thenReturn(Optional.of(applicant));

		String validHash = hasher.fingerprint(
				"AUTH:LOGIN\u0000iss-mob-1\u000077\u0000MOBILE\u0000+919876543210\u0000123456");
		ApplicantAuthChallenge mobileChallenge = new ApplicantAuthChallenge(
				applicant,
				ApplicantAuthChallenge.Purpose.LOGIN,
				ApplicantVerificationChannel.MOBILE,
				hasher.fingerprint("DESTINATION:MOBILE:+919876543210"),
				validHash,
				"iss-mob-1",
				NOW,
				NOW.plusSeconds(300),
				NOW.plusSeconds(60));
		when(challengeRepo.findByIssuanceIdForUpdate("iss-mob-1")).thenReturn(Optional.of(mobileChallenge));

		ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(
				applicantRepo, membershipRepo, new MembershipActivationPolicy(),
				challengeRepo, mock(ApplicationEventPublisher.class), props,
				mock(MobileOtpDelivery.class), mock(EmailOtpDelivery.class),
				hasher, abuseGuard, sessionService, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThrows(InvalidAuthChallengeException.class,
				() -> service.verifyLoginOtp("iss-mob-1", "9876543210", "123456", null));
		verify(sessionService, never()).rotateSession(any(), any(), any());
	}
}
