package com.zaylope.registration.registration;

import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.common.exception.RegistrationAlreadySubmittedException;
import com.zaylope.registration.document.ApplicantDocumentService;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicantRegistrationStateControllerTest {

	private ApplicantDocumentService documentService;
	private LocalValidatorFactoryBean validator;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		documentService = mock(ApplicantDocumentService.class);
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		mockMvc = MockMvcBuilders.standaloneSetup(new ApplicantRegistrationStateController(documentService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void updateProfileUpdatesValidNameAndRejectsInvalidOrSubmitted() throws Exception {
		when(documentService.updateFullName(42L, "Arjun K Nair"))
				.thenReturn(new ApplicantRegistrationStateResponse(
						"+91 •••••• 3210", "a••••@example.com", true, false, "DRAFT", List.of(),
						"Arjun K Nair", "APP-42", true, false, "NONE", "PENDING", "PENDING"));

		mockMvc.perform(put("/api/v1/applicants/me/registration/profile")
						.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fullName\":\"Arjun K Nair\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Arjun K Nair"));

		verify(documentService).updateFullName(42L, "Arjun K Nair");

		// Invalid name containing digits -> rejected by validation
		mockMvc.perform(put("/api/v1/applicants/me/registration/profile")
						.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fullName\":\"Arjun 123\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		// Submitted application -> rejected by service
		when(documentService.updateFullName(42L, "New Name"))
				.thenThrow(new RegistrationAlreadySubmittedException());
		mockMvc.perform(put("/api/v1/applicants/me/registration/profile")
						.requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(42L))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"fullName\":\"New Name\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REGISTRATION_ALREADY_SUBMITTED"));
	}
}
