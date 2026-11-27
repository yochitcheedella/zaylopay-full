package com.zaylope.registration.applicant;

import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicantVerificationStateTest {

	@Test
	void newApplicantsStayUnverifiedWhenTheirMobileNumberChanges() {
		Applicant applicant = new Applicant("Rahul Kumar", "+919876543210", "rahul@example.com");

		applicant.changeMobileNumber("+919876543211");

		assertFalse(applicant.isMobileVerified());
		assertNull(applicant.getMobileVerifiedAt());
	}

	@Test
	void applicantEmailPreservesLocalPartCaseAndNormalizesDomain() {
		Applicant applicant = new Applicant("Test Member", "+919876543210", "User.Tag+Work@EXAMPLE.COM");

		assertEquals("User.Tag+Work@example.com", applicant.getEmail());
		assertTrue(applicant.isEmailCaseSensitive());

		applicant.changeEmailAddress("Other.User@EXAMPLE.COM");

		assertEquals("Other.User@example.com", applicant.getEmail());
		assertTrue(applicant.isEmailCaseSensitive());
	}

	@Test
	void emailAndMobileCanBeVerifiedIndependentlyWithoutMarkingTheOtherVerified() {
		Applicant mobileOnly = new Applicant("Rahul Kumar", "+919876543210", "rahul@example.com");
		Instant mobileVerifiedAt = Instant.parse("2026-11-19T05:00:00Z");
		mobileOnly.markMobileVerified(mobileVerifiedAt);

		assertTrue(mobileOnly.isMobileVerified());
		assertFalse(mobileOnly.isEmailVerified());
		assertTrue(mobileOnly.isContactVerified());
		assertEquals(mobileVerifiedAt, mobileOnly.getMobileVerifiedAt());
		assertNull(mobileOnly.getEmailVerifiedAt());

		Applicant emailOnly = new Applicant("Priya Sharma", "+919876543211", "priya@example.com");
		Instant emailVerifiedAt = Instant.parse("2026-11-19T05:01:00Z");
		emailOnly.markEmailVerified(emailVerifiedAt);

		assertFalse(emailOnly.isMobileVerified());
		assertTrue(emailOnly.isEmailVerified());
		assertTrue(emailOnly.isContactVerified());
		assertNull(emailOnly.getMobileVerifiedAt());
		assertEquals(emailVerifiedAt, emailOnly.getEmailVerifiedAt());
	}
}
