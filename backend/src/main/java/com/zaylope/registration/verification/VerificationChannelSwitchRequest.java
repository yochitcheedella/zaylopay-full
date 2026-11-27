package com.zaylope.registration.verification;

import jakarta.validation.constraints.NotNull;

public record VerificationChannelSwitchRequest(
		@NotNull(message = "Verification channel is required.")
		ApplicantVerificationChannel channel) {
}
