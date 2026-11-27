package com.zaylope.registration.auth;

import com.zaylope.registration.verification.ApplicantVerificationChannel;

record LoginOtpDispatchEvent(
		String issuanceId,
		ApplicantVerificationChannel channel,
		String destination,
		String code) {
	@Override public String toString() { return "LoginOtpDispatchEvent[redacted]"; }
}
