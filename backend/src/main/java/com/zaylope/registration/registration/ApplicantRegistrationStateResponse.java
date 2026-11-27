package com.zaylope.registration.registration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zaylope.registration.document.ApplicantDocumentResponse;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantRegistrationStateResponse(
		String maskedMobileNumber,
		String maskedEmailAddress,
		boolean mobileVerified,
		boolean emailVerified,
		String registrationStatus,
		List<ApplicantDocumentResponse> documents,
		String fullName,
		String applicantReference,
		boolean contactVerified,
		boolean consentAccepted,
		String emailVerificationMethod,
		String paymentStatus,
		String approvalStatus) {

	public ApplicantRegistrationStateResponse(
			String maskedMobileNumber,
			String maskedEmailAddress,
			boolean mobileVerified,
			boolean emailVerified,
			String registrationStatus,
			List<ApplicantDocumentResponse> documents) {
		this(maskedMobileNumber, maskedEmailAddress, mobileVerified, emailVerified, registrationStatus, documents, null, null,
				mobileVerified || emailVerified, false, null, null, null);
	}

	public ApplicantRegistrationStateResponse(
			String maskedMobileNumber,
			String maskedEmailAddress,
			boolean mobileVerified,
			boolean emailVerified,
			String registrationStatus,
			List<ApplicantDocumentResponse> documents,
			String fullName,
			String applicantReference) {
		this(maskedMobileNumber, maskedEmailAddress, mobileVerified, emailVerified, registrationStatus, documents, fullName, applicantReference,
				mobileVerified || emailVerified, false, null, null, null);
	}

	public ApplicantRegistrationStateResponse(
			String maskedMobileNumber,
			String maskedEmailAddress,
			boolean mobileVerified,
			boolean emailVerified,
			String registrationStatus,
			List<ApplicantDocumentResponse> documents,
			String fullName,
			String applicantReference,
			boolean contactVerified,
			boolean consentAccepted) {
		this(maskedMobileNumber, maskedEmailAddress, mobileVerified, emailVerified, registrationStatus, documents,
				fullName, applicantReference, contactVerified, consentAccepted, null, null, null);
	}
}
