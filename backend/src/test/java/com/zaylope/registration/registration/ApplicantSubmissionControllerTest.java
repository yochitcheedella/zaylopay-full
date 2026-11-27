package com.zaylope.registration.registration;

import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.common.exception.OnboardingConsentRequiredException;
import com.zaylope.registration.document.ApplicantDocumentService;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicantSubmissionControllerTest {

	private final ApplicantDocumentService documentService = mock(ApplicantDocumentService.class);
	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ApplicantSubmissionController(documentService))
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void submitPassesConsentAndReturnsSubmittedStatus() throws Exception {
		when(documentService.submit(42L, true, "onboarding-v1"))
				.thenReturn(new ApplicantRegistrationResponse("Registration submitted successfully", "SUBMITTED", "APP-42"));

		mockMvc.perform(post("/api/v1/applicants/me/submit")
						.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"consentAccepted\":true,\"privacyNoticeVersion\":\"onboarding-v1\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SUBMITTED"))
				.andExpect(jsonPath("$.applicantReference").value("APP-42"));

		verify(documentService).submit(42L, true, "onboarding-v1");
	}

	@Test
	void submitWithoutConsentReturnsUnprocessableEntity() throws Exception {
		when(documentService.submit(42L, false, null))
				.thenThrow(new OnboardingConsentRequiredException());

		mockMvc.perform(post("/api/v1/applicants/me/submit")
						.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("ONBOARDING_CONSENT_REQUIRED"));
	}
}
