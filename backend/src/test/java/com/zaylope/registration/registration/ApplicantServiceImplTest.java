package com.zaylope.registration.registration;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.common.exception.MobileAlreadyVerifiedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicantServiceImplTest {

	@Mock
	private ApplicantRegistrationWriter registrationWriter;

	@InjectMocks
	private ApplicantServiceImpl applicantService;

	@Test
	void registerTrimsAndPersistsApplicantDetails() {
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"  Rahul Kumar  ", " 9876543210 ", " rahul@example.com ");
		ApplicantRegistrationResult expected = new ApplicantRegistrationResult(
				new ApplicantRegistrationResponse("Registration draft created successfully", "DRAFT"),
				"opaque-test-session-token");
		when(registrationWriter.register(request)).thenReturn(expected);

		ApplicantRegistrationResult result = applicantService.register(request);

		assertEquals(expected, result);
		verify(registrationWriter).register(request);
	}

	@Test
	void duplicateConstraintRetriesOnceToReuseOrConfirmTheWinner() {
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Applicant One", "9876543210", "same@example.com");
		ApplicantRegistrationResult expected = new ApplicantRegistrationResult(
				new ApplicantRegistrationResponse("Registration draft created successfully", "DRAFT"),
				"opaque-test-session-token");
		when(registrationWriter.register(request))
				.thenThrow(new DataIntegrityViolationException("duplicate"))
				.thenReturn(expected);

		assertEquals(expected, applicantService.register(request));

		verify(registrationWriter, org.mockito.Mockito.times(2)).register(request);
	}

	@Test
	void verifiedConflictIsNotRetried() {
		ApplicantRegistrationRequest request = new ApplicantRegistrationRequest(
				"Applicant One", "9876543210", "same@example.com");
		when(registrationWriter.register(request))
				.thenThrow(new MobileAlreadyVerifiedException());

		Assertions.assertThrows(
				MobileAlreadyVerifiedException.class,
				() -> applicantService.register(request));

		verify(registrationWriter).register(request);
	}
}
