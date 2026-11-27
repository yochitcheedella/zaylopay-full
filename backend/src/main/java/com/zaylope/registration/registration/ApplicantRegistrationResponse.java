package com.zaylope.registration.registration;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApplicantRegistrationResponse(
		String message,
		String status,
		String applicantReference) {

	public ApplicantRegistrationResponse(String message, String status) {
		this(message, status, null);
	}
}
