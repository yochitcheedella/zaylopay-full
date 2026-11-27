package com.zaylope.registration.registration;

public record ApplicantSubmissionRequest(
		Boolean consentAccepted,
		String privacyNoticeVersion) {
}
