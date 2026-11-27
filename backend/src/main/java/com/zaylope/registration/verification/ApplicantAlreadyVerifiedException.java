package com.zaylope.registration.verification;

public class ApplicantAlreadyVerifiedException extends RuntimeException {

	private final ApplicantVerificationChannel channel;

	public ApplicantAlreadyVerifiedException(ApplicantVerificationChannel channel) {
		this.channel = channel;
	}

	public ApplicantVerificationChannel channel() {
		return channel;
	}
}
