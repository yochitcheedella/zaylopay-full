package com.zaylope.registration.verification;

import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicantVerificationControllerTest {

	private final ApplicantVerificationService verificationService =
			mock(ApplicantVerificationService.class);
	private final LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
	private final MockMvc mockMvc;

	ApplicantVerificationControllerTest() {
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(
						new ApplicantVerificationController(verificationService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void mobileSendReturnsOnlyMaskedDestinationAndCooldown() throws Exception {
		when(verificationService.sendMobileCode(42L))
				.thenReturn(new OtpDispatchResponse("+91 •••••• 3210", 60));

		mockMvc.perform(post("/api/v1/applicants/me/verification/mobile/send")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maskedDestination").value("+91 •••••• 3210"))
				.andExpect(jsonPath("$.resendAfterSeconds").value(60))
				.andExpect(jsonPath("$.code").doesNotExist())
				.andExpect(jsonPath("$.otp").doesNotExist());

		verify(verificationService).sendMobileCode(42L);
	}

	@Test
	void verificationRequiresExactlySixNumericDigits() throws Exception {
		mockMvc.perform(post("/api/v1/applicants/me/verification/mobile/verify")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"12a456\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void unavailableDeliveryReturnsAnExplicitServiceError() throws Exception {
		when(verificationService.sendMobileCode(42L))
				.thenThrow(new VerificationDeliveryUnavailableException());

		mockMvc.perform(post("/api/v1/applicants/me/verification/mobile/send")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("VERIFICATION_DELIVERY_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value(
						"Verification code delivery is temporarily unavailable. Please try again shortly."));
	}

	@Test
	void mobileChangeUsesTheApplicantSessionAndReturnsOnlyAMaskedNumber() throws Exception {
		when(verificationService.changeMobileNumber(42L, "+919876543210"))
				.thenReturn(new MobileNumberChangeResponse("+91 •••••• 3210", false));

		mockMvc.perform(put("/api/v1/applicants/me/verification/mobile")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mobileNumber\":\"+919876543210\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maskedDestination").value("+91 •••••• 3210"))
				.andExpect(jsonPath("$.mobileVerified").value(false))
				.andExpect(jsonPath("$.mobileNumber").doesNotExist());

		verify(verificationService).changeMobileNumber(42L, "+919876543210");
	}

	@Test
	void mobileChangeEnforcesRequestShapeValidation() throws Exception {
		mockMvc.perform(put("/api/v1/applicants/me/verification/mobile")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mobileNumber\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void mobileChangeRateLimitIsExplicit() throws Exception {
		when(verificationService.changeMobileNumber(42L, "+919876543210"))
				.thenThrow(new MobileNumberChangeRateLimitException(60));

		mockMvc.perform(put("/api/v1/applicants/me/verification/mobile")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"mobileNumber\":\"+919876543210\"}"))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("MOBILE_CHANGE_RATE_LIMITED"))
				.andExpect(jsonPath("$.retryAfterSeconds").value(60));
	}

	@Test
	void switchChannelInvalidatesPreviousAndReturnsVerificationState() throws Exception {
		java.time.Instant now = java.time.Instant.parse("2026-11-20T00:00:00Z");
		VerificationStateResponse state = new VerificationStateResponse(
				now,
				"MOCK",
				new VerificationStateResponse.ChannelState("+91 •••••• 3210", false, true, null, null, "NOT_ACTIVE"),
				new VerificationStateResponse.ChannelState("t••••@example.com", false, true, null, null, "NOT_SENT"),
				false);
		when(verificationService.switchChannel(42L, ApplicantVerificationChannel.EMAIL)).thenReturn(state);

		mockMvc.perform(post("/api/v1/applicants/me/verification/switch")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"channel\":\"EMAIL\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contactVerified").value(false))
				.andExpect(jsonPath("$.email.maskedDestination").value("t••••@example.com"));

		verify(verificationService).switchChannel(42L, ApplicantVerificationChannel.EMAIL);
	}

	@Test
	void emailSendAndVerifyWorkIndependently() throws Exception {
		when(verificationService.sendEmailCode(42L))
				.thenReturn(new OtpDispatchResponse("t••••@example.com", 60));
		when(verificationService.verifyEmailCode(42L, "654321"))
				.thenReturn(new ApplicantVerificationStatusResponse(false, true));

		mockMvc.perform(post("/api/v1/applicants/me/verification/email/send")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.maskedDestination").value("t••••@example.com"));

		mockMvc.perform(post("/api/v1/applicants/me/verification/email/verify")
						.requestAttr(
								ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
								new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"654321\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mobileVerified").value(false))
				.andExpect(jsonPath("$.emailVerified").value(true))
				.andExpect(jsonPath("$.contactVerified").value(true));
	}
}

