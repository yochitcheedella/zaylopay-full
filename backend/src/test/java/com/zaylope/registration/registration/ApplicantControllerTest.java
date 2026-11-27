package com.zaylope.registration.registration;

import com.zaylope.registration.common.exception.DuplicateEmailException;
import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.common.exception.MobileAlreadyVerifiedException;
import com.zaylope.registration.config.ApplicantSessionProperties;
import com.zaylope.registration.session.ApplicantAccessSessionRepository;
import com.zaylope.registration.session.ApplicantSessionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApplicantControllerTest {

	private ApplicantService applicantService;
	private LocalValidatorFactoryBean validator;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		applicantService = org.mockito.Mockito.mock(ApplicantService.class);
		validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		ApplicantSessionService sessionService = new ApplicantSessionService(
				Mockito.mock(ApplicantAccessSessionRepository.class),
				new ApplicantSessionProperties("ZAYLOPE_APPLICANT", Duration.ofMinutes(30), false),
				Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC));
		mockMvc = MockMvcBuilders.standaloneSetup(new ApplicantController(applicantService, sessionService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.setValidator(validator)
				.build();
	}

	@AfterEach
	void tearDown() {
		validator.close();
	}

	@Test
	void exactThreeFieldPayloadReturnsCreatedResponseWithoutInternalId() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenReturn(new ApplicantRegistrationResult(
						new ApplicantRegistrationResponse("Registration draft created successfully", "DRAFT"),
						"opaque-test-session-token"));

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "fullName": "Sai Sudheer",
								  "mobileNumber": "9876543210",
								  "email": "kishore.test@example.com"
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.message").value("Registration draft created successfully"))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.applicantId").doesNotExist())
				.andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
				.andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));

		ArgumentCaptor<ApplicantRegistrationRequest> requestCaptor =
				ArgumentCaptor.forClass(ApplicantRegistrationRequest.class);
		verify(applicantService).register(requestCaptor.capture());
		org.junit.jupiter.api.Assertions.assertEquals("Sai Sudheer", requestCaptor.getValue().fullName());
		org.junit.jupiter.api.Assertions.assertEquals("9876543210", requestCaptor.getValue().mobileNumber());
		org.junit.jupiter.api.Assertions.assertEquals(
				"kishore.test@example.com", requestCaptor.getValue().email());
	}

	@Test
	void blankNameReturnsFieldValidationError() throws Exception {
		assertInvalidRequest("""
				{
				  "fullName": " ",
				  "mobileNumber": "9876543210",
				  "email": "kishore.test@example.com"
				}
				""", "fullName");
	}

	@Test
	void missingNameReturnsFieldValidationError() throws Exception {
		assertInvalidRequest("""
				{
				  "mobileNumber": "9876543210",
				  "email": "rahul@example.com"
				}
				""", "fullName");
	}

	@Test
	void malformedMobileReturnsFieldValidationError() throws Exception {
		assertInvalidRequest("""
				{
				  "fullName": "Rahul Kumar",
				  "mobileNumber": "not-a-number",
				  "email": "rahul@example.com"
				}
				""", "mobileNumber");
	}

	@Test
	void malformedEmailReturnsFieldValidationError() throws Exception {
		assertInvalidRequest("""
				{
				  "fullName": "Rahul Kumar",
				  "mobileNumber": "9876543210",
				  "email": "not-an-email"
				}
				""", "email");
	}

	@Test
	void databaseFailureReturnsServiceUnavailable() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"));

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
	}

	@Test
	void transactionCreationFailureReturnsSafeServiceUnavailableResponse() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenThrow(new CannotCreateTransactionException("database authentication failed"));

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
						containsString("authentication failed"))));
	}

	@Test
	void schemaMismatchIsNotReportedAsDatabaseUnavailableOrExposed() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenThrow(new InvalidDataAccessResourceUsageException(
						"Table 'zaylope.mobile_registration_locks' doesn't exist"));

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("DATABASE_CONFIGURATION_ERROR"))
				.andExpect(jsonPath("$.detail").value("The database schema is incompatible with this request."))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
						containsString("mobile_registration_locks"))));
	}

	@Test
	void databaseConstraintViolationReturnsConflict() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenThrow(new DataIntegrityViolationException("duplicate key"));

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("REGISTRATION_CONFLICT"));
	}

	@Test
	void verifiedMobileRegistrationReturnsClearConflict() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenThrow(new MobileAlreadyVerifiedException());

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("MOBILE_ALREADY_VERIFIED"))
				.andExpect(jsonPath("$.detail").value("This mobile number has already been verified."));
	}

	@Test
	void duplicateEmailReturnsClearConflict() throws Exception {
		when(applicantService.register(any(ApplicantRegistrationRequest.class)))
				.thenThrow(new DuplicateEmailException());

		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validRequest()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
	}

	private void assertInvalidRequest(String request, String field) throws Exception {
		mockMvc.perform(post("/api/v1/applicants/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(request))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[?(@.field == '" + field + "')]").isNotEmpty());

		verify(applicantService, never()).register(any(ApplicantRegistrationRequest.class));
	}

	private String validRequest() {
		return """
				{
				  "fullName": "Sai Sudheer",
				  "mobileNumber": "9876543210",
				  "email": "kishore.test@example.com"
				}
				""";
	}
}
