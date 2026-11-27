package com.zaylope.registration.verification;

public record ApplicantVerificationStatusResponse(boolean mobileVerified, boolean emailVerified, boolean contactVerified) {
	public ApplicantVerificationStatusResponse(boolean mobileVerified, boolean emailVerified) {
		this(mobileVerified, emailVerified, mobileVerified || emailVerified);
	}
}
