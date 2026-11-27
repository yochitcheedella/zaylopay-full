package com.zaylope.registration.verification;

/** Transient delivery secret, never persisted or logged. */
public record ContactOtpDispatchEvent(long applicantId, ApplicantVerificationChannel channel,
        String issuanceId, String destination, String code) {
    @Override public String toString() { return "ContactOtpDispatchEvent[redacted]"; }
}
