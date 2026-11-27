package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.config.ApplicantSessionProperties;
import com.zaylope.registration.config.RegistrationProperties;
import com.zaylope.registration.registration.ApplicantRegistrationLock;
import com.zaylope.registration.registration.ApplicantRegistrationLockRepository;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantVerificationChannel;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GoogleAuthControllerTest {

	private static final Instant NOW = Instant.parse("2026-11-25T10:00:00Z");

	private GoogleTokenVerifierService tokenVerifier;
	private ApplicantRepository applicantRepository;
	private ApplicantRegistrationLockRepository lockRepository;
	private AffiliateMembershipRepository membershipRepository;
	private ApplicantSessionService sessionService;
	private ApplicantOnboardingSessionService onboardingSessionService;
	private ApplicantAuthChallengeService authChallengeService;
	private LocalValidatorFactoryBean validator;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		tokenVerifier = Mockito.mock(GoogleTokenVerifierService.class);
		applicantRepository = Mockito.mock(ApplicantRepository.class);
		lockRepository = Mockito.mock(ApplicantRegistrationLockRepository.class);
		membershipRepository = Mockito.mock(AffiliateMembershipRepository.class);
		sessionService = Mockito.mock(ApplicantSessionService.class);
		onboardingSessionService = Mockito.mock(ApplicantOnboardingSessionService.class);
		authChallengeService = Mockito.mock(ApplicantAuthChallengeService.class);

		when(tokenVerifier.isConfigured()).thenReturn(true);

		ApplicantSessionProperties properties = new ApplicantSessionProperties("ZAYLOPE_APPLICANT",
				Duration.ofMinutes(30), false);
		ApplicantSessionService realCookieHelper = new ApplicantSessionService(
				Mockito.mock(com.zaylope.registration.session.ApplicantAccessSessionRepository.class),
				properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
		Mockito.doAnswer(inv -> {
			realCookieHelper.issueSessionCookie(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(sessionService).issueSessionCookie(any(), any());
		Mockito.doAnswer(inv -> {
			realCookieHelper.issueOnboardingCookie(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(sessionService).issueOnboardingCookie(any(), any());
		Mockito.doAnswer(inv -> {
			realCookieHelper.clearOnboardingCookie(inv.getArgument(0));
			return null;
		}).when(sessionService).clearOnboardingCookie(any());
		when(sessionService.extractApplicantToken(any()))
				.thenAnswer(inv -> realCookieHelper.extractApplicantToken(inv.getArgument(0)));
		when(sessionService.extractOnboardingToken(any()))
				.thenAnswer(inv -> realCookieHelper.extractOnboardingToken(inv.getArgument(0)));

		GoogleAuthService googleAuthService = new GoogleAuthService(
				tokenVerifier,
				applicantRepository,
				lockRepository,
				membershipRepository,
				sessionService,
				onboardingSessionService,
				authChallengeService,
				new RegistrationProperties(Duration.ofMinutes(30)),
				Clock.fixed(NOW, ZoneOffset.UTC));

		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		GoogleAuthController controller = new GoogleAuthController(googleAuthService, sessionService);
		mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void authenticate_whenGoogleDisabled_returnsServiceUnavailable() throws Exception {
		when(tokenVerifier.isConfigured()).thenReturn(false);

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"any\"}"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("GOOGLE_AUTH_UNAVAILABLE"));
	}

	@Test
	void authenticate_whenTokenInvalid_returnsUnauthorized() throws Exception {
		when(tokenVerifier.verifyToken("bad-token")).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"bad-token\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_GOOGLE_TOKEN"));
	}

	@Test
	void authenticate_whenReturningUserByGoogleSub_rotatesSessionAndReturnsAuthenticated() throws Exception {
		var payload = new GoogleTokenVerifierService.GoogleUserPayload(
				"sub-12345", "test@gmail.com", true, "Test User", "https://pic.url", null);
		when(tokenVerifier.verifyToken("valid-token")).thenReturn(Optional.of(payload));
		when(tokenVerifier.isAuthoritativeGoogleEmail(payload)).thenReturn(true);

		Applicant applicant = Applicant.createGoogleApplicant(
				"Test User", "+919876543210", "test@gmail.com", "sub-12345", "test@gmail.com", "https://pic.url");
		ReflectionTestUtils.setField(applicant, "applicantId", 10L);
		applicant.markEmailVerified(NOW, Applicant.EMAIL_METHOD_GOOGLE);
		when(applicantRepository.findByGoogleSub("sub-12345")).thenReturn(Optional.of(applicant));
		when(sessionService.rotateSession(applicant, ApplicantSessionScope.AUTHENTICATED, "old-tok"))
				.thenReturn("rotated-session");

		mockMvc.perform(post("/api/v1/auth/google")
				.cookie(new Cookie("ZAYLOPE_APPLICANT", "old-tok"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"valid-token\"}"))
				.andExpect(status().isOk())
				.andExpect(header().stringValues("Set-Cookie",
						hasItem(containsString("ZAYLOPE_APPLICANT=rotated-session"))))
				.andExpect(jsonPath("$.status").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.eligibleStep").value("PAYMENT"));
	}

	@Test
	void authenticate_whenExistingVerifiedUserByEmail_createsLinkingOnboardingSessionWithoutAutoSendingOtp()
			throws Exception {
		var payload = new GoogleTokenVerifierService.GoogleUserPayload(
				"sub-99999", "existing@gmail.com", true, "Existing User", null, null);
		when(tokenVerifier.verifyToken("valid-token")).thenReturn(Optional.of(payload));
		when(tokenVerifier.isAuthoritativeGoogleEmail(payload)).thenReturn(true);
		when(applicantRepository.findByGoogleSub("sub-99999")).thenReturn(Optional.empty());

		Applicant existing = new Applicant("Existing User", "+919876543210", "existing@gmail.com");
		ReflectionTestUtils.setField(existing, "applicantId", 42L);
		existing.markMobileVerified(NOW);
		when(applicantRepository.findByActiveEmailKeyForUpdate("existing@gmail.com")).thenReturn(Optional.of(existing));
		when(onboardingSessionService.create(ApplicantOnboardingSession.FlowType.GOOGLE_LINK_ACCOUNT, payload, 42L))
				.thenReturn("onboarding-link-token");

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"valid-token\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", containsString("ZAYLOPE_ONBOARDING=onboarding-link-token")))
				.andExpect(jsonPath("$.status").value("LINKING_CONFIRMATION_REQUIRED"))
				.andExpect(jsonPath("$.linkingChannel").value("MOBILE"))
				.andExpect(jsonPath("$.maskedMobileNumber").value(containsString("3210")));

		verify(authChallengeService, never()).sendLinkingOtp(42L);
	}

	@Test
	void authenticate_whenNewUser_createsOnboardingSessionCookie() throws Exception {
		var payload = new GoogleTokenVerifierService.GoogleUserPayload(
				"sub-new", "new@gmail.com", true, "New User", "https://lh3.googleusercontent.com/a/photo", null);
		when(tokenVerifier.verifyToken("valid-token")).thenReturn(Optional.of(payload));
		when(tokenVerifier.isAuthoritativeGoogleEmail(payload)).thenReturn(true);
		when(applicantRepository.findByGoogleSub("sub-new")).thenReturn(Optional.empty());
		when(applicantRepository.findByEmailAndEmailVerifiedAtIsNotNull("new@gmail.com"))
				.thenReturn(Optional.empty());
		when(applicantRepository.findByActiveEmailKeyForUpdate("new@gmail.com")).thenReturn(Optional.empty());
		when(onboardingSessionService.create(ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE, payload,
				null))
				.thenReturn("onboarding-token-123");

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"valid-token\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", containsString("ZAYLOPE_ONBOARDING=onboarding-token-123")))
				.andExpect(jsonPath("$.status").value("NEEDS_PROFILE_COMPLETION"))
				.andExpect(jsonPath("$.authoritativeEmail").value(true))
				.andExpect(jsonPath("$.pictureUrl").value("https://lh3.googleusercontent.com/a/photo"));
	}

	@Test
	void authenticate_whenLoginIntentAndUnregisteredVerifiedGoogleAccount_returnsAccountNotFoundWithoutCreatingApplicant()
			throws Exception {
		var payload = new GoogleTokenVerifierService.GoogleUserPayload(
				"sub-unreg", "unreg@gmail.com", true, "Unreg User", "https://pic.url/u", null);
		when(tokenVerifier.verifyToken("unreg-token")).thenReturn(Optional.of(payload));
		when(tokenVerifier.isAuthoritativeGoogleEmail(payload)).thenReturn(true);
		when(applicantRepository.findByGoogleSub("sub-unreg")).thenReturn(Optional.empty());
		when(applicantRepository.findByEmailAndEmailVerifiedAtIsNotNull("unreg@gmail.com"))
				.thenReturn(Optional.empty());
		when(applicantRepository.findByActiveEmailKeyForUpdate("unreg@gmail.com")).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"unreg-token\",\"intent\":\"LOGIN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCOUNT_NOT_FOUND"))
				.andExpect(jsonPath("$.email").value("unreg@gmail.com"))
				.andExpect(jsonPath("$.eligibleStep").value("REGISTER"));

		verify(applicantRepository, never()).save(any());
		verify(applicantRepository, never()).saveAndFlush(any());
		verify(onboardingSessionService, never()).create(any(), any(), any());
	}

	@Test
	void authenticate_whenGoogleEmailNotVerified_rejectsWithoutLinkingOrDisclosingExistence() throws Exception {
		var payload = new GoogleTokenVerifierService.GoogleUserPayload(
				"sub-unverified-email", "victim@example.com", false, "Spoofer", null, null);
		when(tokenVerifier.verifyToken("unver-tok")).thenReturn(Optional.of(payload));
		when(applicantRepository.findByGoogleSub("sub-unverified-email")).thenReturn(Optional.empty());

		mockMvc.perform(post("/api/v1/auth/google")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"credential\":\"unver-tok\",\"intent\":\"LOGIN\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("GOOGLE_EMAIL_UNVERIFIED"));

		verify(applicantRepository, never()).findByActiveEmailKeyForUpdate(any());
		verify(onboardingSessionService, never()).create(any(), any(), any());
	}

	@Test
	void completeProfile_whenThirdPartyNonAuthoritativeEmail_doesNotAutoVerifyEmailAndUsesOnboardingScope()
			throws Exception {
		ApplicantOnboardingSession session = new ApplicantOnboardingSession(
				"hash",
				ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE,
				"sub-yahoo",
				"user@yahoo.com",
				true,
				null,
				"Yahoo User",
				null,
				null,
				NOW,
				NOW.plusSeconds(900));
		when(onboardingSessionService.lockActiveForUpdate("onboard-tok")).thenReturn(Optional.of(session));
		when(tokenVerifier.isAuthoritativeGoogleEmail(any())).thenReturn(false);
		when(lockRepository.findByMobileNumberForUpdate("+919876543210"))
				.thenReturn(Optional.of(new ApplicantRegistrationLock("+919876543210")));
		when(sessionService.rotateSession(any(Applicant.class), eq(ApplicantSessionScope.ONBOARDING), any()))
				.thenReturn("applicant-onboard-tok");

		mockMvc.perform(post("/api/v1/auth/google/complete-profile")
				.cookie(new Cookie("ZAYLOPE_ONBOARDING", "onboard-tok"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"Yahoo User\",\"mobileNumber\":\"9876543210\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.emailVerified").value(false))
				.andExpect(jsonPath("$.emailVerificationMethod").value("NONE"))
				.andExpect(jsonPath("$.eligibleStep").value("VERIFY"));
	}

	@Test
	void completeProfile_whenAuthoritativeGoogleEmail_skipsOtpAndTargetsPaymentDirectly() throws Exception {
		ApplicantOnboardingSession session = new ApplicantOnboardingSession(
				"hash",
				ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE,
				"sub-gmail",
				"user@gmail.com",
				true,
				null,
				"Gmail User",
				null,
				null,
				NOW,
				NOW.plusSeconds(900));
		when(onboardingSessionService.lockActiveForUpdate("onboard-tok")).thenReturn(Optional.of(session));
		when(tokenVerifier.isAuthoritativeGoogleEmail(any())).thenReturn(true);
		when(lockRepository.findByMobileNumberForUpdate("+919876543210"))
				.thenReturn(Optional.of(new ApplicantRegistrationLock("+919876543210")));
		when(sessionService.rotateSession(any(Applicant.class), eq(ApplicantSessionScope.AUTHENTICATED), any()))
				.thenReturn("applicant-auth-tok");

		mockMvc.perform(post("/api/v1/auth/google/complete-profile")
				.cookie(new Cookie("ZAYLOPE_ONBOARDING", "onboard-tok"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"fullName\":\"Gmail User\",\"mobileNumber\":\"9876543210\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.emailVerified").value(true))
				.andExpect(jsonPath("$.emailVerificationMethod").value("GOOGLE"))
				.andExpect(jsonPath("$.eligibleStep").value("PAYMENT"));
	}

	@Test
	void verifyLinkingOtp_rejectsOverwritingExistingDifferentGoogleSub() throws Exception {
		ApplicantOnboardingSession session = new ApplicantOnboardingSession(
				"hash",
				ApplicantOnboardingSession.FlowType.GOOGLE_LINK_ACCOUNT,
				"new-attacker-sub",
				"victim@gmail.com",
				true,
				null,
				"Attacker",
				null,
				42L,
				NOW,
				NOW.plusSeconds(900));
		when(onboardingSessionService.lockActiveForUpdate("link-tok")).thenReturn(Optional.of(session));

		Applicant existing = Applicant.createGoogleApplicant(
				"Victim", "+919876543210", "victim@gmail.com", "original-google-sub", "victim@gmail.com", null);
		ReflectionTestUtils.setField(existing, "applicantId", 42L);
		when(applicantRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(existing));

		mockMvc.perform(post("/api/v1/auth/google/link/verify")
				.cookie(new Cookie("ZAYLOPE_ONBOARDING", "link-tok"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"otpCode\":\"123456\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("GOOGLE_IDENTITY_CONFLICT"));

		assertEquals("original-google-sub", existing.getGoogleSub());
		assertFalse(session.isActiveAt(NOW.plusSeconds(10)) && session.getTargetApplicantId() == null);
	}
}
