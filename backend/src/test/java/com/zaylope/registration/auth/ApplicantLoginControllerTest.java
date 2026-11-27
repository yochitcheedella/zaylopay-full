package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.config.ApplicantSessionProperties;
import com.zaylope.registration.session.ApplicantAccessSessionRepository;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicantLoginControllerTest {

	private ApplicantRepository applicantRepository;
	private ApplicantSessionService applicantSessionService;
	private ApplicantOnboardingSessionService onboardingSessionService;
	private ApplicantAuthChallengeService authChallengeService;
	private AffiliateMembershipRepository membershipRepository;
	private com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository;
	private LocalValidatorFactoryBean validator;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		applicantRepository = Mockito.mock(ApplicantRepository.class);
		applicantSessionService = Mockito.mock(ApplicantSessionService.class);
		onboardingSessionService = Mockito.mock(ApplicantOnboardingSessionService.class);
		authChallengeService = Mockito.mock(ApplicantAuthChallengeService.class);
		membershipRepository = Mockito.mock(AffiliateMembershipRepository.class);
		paymentOrderRepository = Mockito.mock(com.zaylope.registration.payment.PaymentOrderRepository.class);
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();

		ApplicantSessionProperties properties =
				new ApplicantSessionProperties("ZAYLOPE_APPLICANT", Duration.ofMinutes(30), false);
		ApplicantSessionService realHelper = new ApplicantSessionService(
				Mockito.mock(ApplicantAccessSessionRepository.class),
				properties,
				Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC));
		when(applicantSessionService.extractApplicantToken(any())).thenAnswer(inv -> realHelper.extractApplicantToken(inv.getArgument(0)));
		when(applicantSessionService.extractOnboardingToken(any())).thenAnswer(inv -> realHelper.extractOnboardingToken(inv.getArgument(0)));
		Mockito.doAnswer(inv -> {
			realHelper.issueSessionCookie(inv.getArgument(0), inv.getArgument(1));
			return null;
		}).when(applicantSessionService).issueSessionCookie(any(), any());
		Mockito.doAnswer(inv -> {
			realHelper.clearSessionCookie(inv.getArgument(0));
			return null;
		}).when(applicantSessionService).clearSessionCookie(any());
		Mockito.doAnswer(inv -> {
			realHelper.clearOnboardingCookie(inv.getArgument(0));
			return null;
		}).when(applicantSessionService).clearOnboardingCookie(any());

		mockMvc = MockMvcBuilders.standaloneSetup(new ApplicantLoginController(
						applicantRepository,
						applicantSessionService,
						onboardingSessionService,
						authChallengeService,
						membershipRepository,
						new com.zaylope.registration.affiliate.MembershipActivationPolicy(),
						paymentOrderRepository))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void legacyIdentifierOnlyLoginEndpointIsRemoved() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"9876543210\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void requestLoginOtpReturnsConsistentNonEnumeratingResponse() throws Exception {
		when(authChallengeService.requestLoginOtp("9876543210"))
				.thenReturn(new ApplicantAuthChallengeService.LoginOtpRequestResult(
						true,
						"challenge-1",
						60,
						300,
						Instant.parse("2026-11-25T10:00:00Z"),
						Instant.parse("2026-11-25T10:05:00Z"),
						Instant.parse("2026-11-25T10:01:00Z"),
						"If your details match an eligible account, you'll receive a 6-digit verification code."));

		mockMvc.perform(post("/api/v1/auth/login/otp/request")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"identifier\":\"9876543210\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accepted").value(true))
				.andExpect(jsonPath("$.challengeId").value("challenge-1"))
				.andExpect(jsonPath("$.serverTime").value("2026-11-25T10:00:00Z"))
				.andExpect(jsonPath("$.expiresAt").value("2026-11-25T10:05:00Z"))
				.andExpect(jsonPath("$.resendAvailableAt").value("2026-11-25T10:01:00Z"))
				.andExpect(jsonPath("$.maskedDestination").doesNotExist());
	}

	@Test
	void verifyLoginOtpIssuesRotatedCookieAndReturnsEligibleStep() throws Exception {
		Applicant applicant = new Applicant("Priya Sharma", "+919876543210", "priya@example.com");
		applicant.markMobileVerified(Instant.parse("2026-11-25T09:00:00Z"));
		when(authChallengeService.verifyLoginOtp("challenge-1", "9876543210", "123456", null))
				.thenReturn(new ApplicantAuthChallengeService.LoginOtpVerifyResult(
						applicant, ApplicantSessionScope.AUTHENTICATED, "new-session-tok", false));

		mockMvc.perform(post("/api/v1/auth/login/otp/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"challengeId\":\"challenge-1\",\"identifier\":\"9876543210\",\"code\":\"123456\"}"))
				.andExpect(status().isOk())
				.andExpect(header().string("Set-Cookie", containsString("ZAYLOPE_APPLICANT=new-session-tok")))
				.andExpect(jsonPath("$.authenticated").value(true))
				.andExpect(jsonPath("$.sessionScope").value("AUTHENTICATED"))
				.andExpect(jsonPath("$.eligibleStep").value("PAYMENT"));
	}

	@Test
	void sessionEndpointResolvesAllFourStepDestinationsWithoutReview() throws Exception {
		when(applicantSessionService.findActiveSession("tok-acct"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						10L, ApplicantSessionScope.ONBOARDING)));
		Applicant incomplete = new Applicant("", "+919876543210", "a@example.com");
		when(applicantRepository.findById(10L)).thenReturn(Optional.of(incomplete));
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "tok-acct")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleStep").value("ACCOUNT"));

		when(applicantSessionService.findActiveSession("tok-verify"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						11L, ApplicantSessionScope.ONBOARDING)));
		Applicant unverified = new Applicant("Priya Sharma", "+919876543211", "v@example.com");
		when(applicantRepository.findById(11L)).thenReturn(Optional.of(unverified));
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "tok-verify")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleStep").value("VERIFY"));

		when(applicantSessionService.findActiveSession("tok-pay"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						12L, ApplicantSessionScope.AUTHENTICATED)));
		Applicant verified = new Applicant("Priya Sharma", "+919876543212", "p@example.com");
		org.springframework.test.util.ReflectionTestUtils.setField(verified, "applicantId", 12L);
		verified.markMobileVerified(Instant.parse("2026-11-25T09:00:00Z"));
		when(applicantRepository.findById(12L)).thenReturn(Optional.of(verified));
		when(paymentOrderRepository.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(12L, "CREATED"))
				.thenReturn(Optional.empty());
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "tok-pay")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleStep").value("PAYMENT"));

		when(paymentOrderRepository.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(12L, "CREATED"))
				.thenReturn(Optional.of(new com.zaylope.registration.payment.PaymentOrder(
						12L, "order_pend_1", 59900L, "INR", "rcpt_1")));
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "tok-pay")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleStep").value("PAYMENT_STATUS"));

		when(applicantSessionService.findActiveSession("tok-rec"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						13L, ApplicantSessionScope.AUTHENTICATED)));
		Applicant paidNotActivated = new Applicant("Priya Sharma", "+919876543213", "r@example.com");
		org.springframework.test.util.ReflectionTestUtils.setField(paidNotActivated, "applicantId", 13L);
		paidNotActivated.markMobileVerified(Instant.parse("2026-11-25T09:00:00Z"));
		paidNotActivated.markPaymentPaid();
		when(applicantRepository.findById(13L)).thenReturn(Optional.of(paidNotActivated));
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "tok-rec")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleStep").value("ACTIVATION_RECOVERY"));

		when(applicantSessionService.findActiveSession("tok-dash"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						14L, ApplicantSessionScope.AUTHENTICATED)));
		Applicant activeApplicant = new Applicant("Priya Sharma", "+919876543214", "d@example.com");
		org.springframework.test.util.ReflectionTestUtils.setField(activeApplicant, "applicantId", 14L);
		activeApplicant.markMobileVerified(Instant.parse("2026-11-25T09:00:00Z"));
		activeApplicant.recordPrivacyConsent(Instant.parse("2026-11-25T09:01:00Z"), "onboarding-v1");
		activeApplicant.markRegistrationSubmitted(Instant.parse("2026-11-25T09:01:00Z"));
		activeApplicant.markPaymentPaid();
		when(applicantRepository.findById(14L)).thenReturn(Optional.of(activeApplicant));
		when(membershipRepository.findByApplicantId(14L))
				.thenReturn(Optional.of(new com.zaylope.registration.affiliate.AffiliateMembership(
						14L, "ZP-261126-001", "ZPREF1", null, Instant.parse("2026-11-25T09:02:00Z"), "LIFETIME")));
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "tok-dash")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eligibleStep").value("DASHBOARD"))
				.andExpect(jsonPath("$.canAccessDashboard").value(true));
	}

	@Test
	void sessionEndpointReturnsAnonymousStateWhenNoCookiePresent() throws Exception {
		mockMvc.perform(get("/api/v1/auth/session"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(false))
				.andExpect(jsonPath("$.sessionState").value("NONE"));
	}

	@Test
	void sessionEndpointReturnsMaskedContactsWhenActiveSessionPresent() throws Exception {
		Applicant applicant = new Applicant("Priya Sharma", "+919876543210", "priya@example.com");
		when(applicantSessionService.findActiveSession("valid-token"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						42L, ApplicantSessionScope.AUTHENTICATED)));
		when(applicantRepository.findById(42L)).thenReturn(Optional.of(applicant));

		mockMvc.perform(get("/api/v1/auth/session")
						.cookie(new Cookie("ZAYLOPE_APPLICANT", "valid-token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authenticated").value(true))
				.andExpect(jsonPath("$.sessionState").value("ACTIVE"))
				.andExpect(jsonPath("$.fullName").value("Priya Sharma"))
				.andExpect(jsonPath("$.maskedMobileNumber").value("+91 •••••• 3210"))
				.andExpect(jsonPath("$.maskedEmailAddress").value("p••••@example.com"))
				.andExpect(jsonPath("$.mobileNumber").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void onboardingCapabilityIsNotPresentedAsVerifiedIdentity() throws Exception {
		Applicant draft = new Applicant("Saved Applicant", "+919876543210", "saved@gmail.com");
		when(applicantSessionService.findActiveSession("draft-token")).thenReturn(Optional.of(
				new ApplicantAccessSessionRepository.ActiveSessionProjection(42L, ApplicantSessionScope.ONBOARDING)));
		when(applicantRepository.findById(42L)).thenReturn(Optional.of(draft));
		mockMvc.perform(get("/api/v1/auth/session").cookie(new Cookie("ZAYLOPE_APPLICANT", "draft-token")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.identityAuthenticated").value(false))
				.andExpect(jsonPath("$.sessionScope").value("ONBOARDING"))
				.andExpect(jsonPath("$.canAccessDashboard").value(false));
	}

	@Test
	void logoutRevokesOnlyCurrentSessionAndLogoutAllRevokesAllSessions() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.cookie(new Cookie("ZAYLOPE_APPLICANT", "token-to-revoke")))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

		verify(applicantSessionService).revoke("token-to-revoke");
		verify(applicantSessionService, never()).revokeAll(any(Long.class));

		mockMvc.perform(post("/api/v1/auth/logout-all")
						.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isNoContent())
				.andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));

		verify(applicantSessionService).revokeAll(42L);
	}
}
