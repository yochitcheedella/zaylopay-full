package com.zaylope.registration.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OtpCodeRequest(
		@NotBlank(message = "Enter the six-digit verification code.")
		@Pattern(regexp = "[0-9]{6}", message = "Enter a six-digit numeric verification code.")
		String code) {
	@Override public String toString() { return "OtpCodeRequest[code=REDACTED]"; }
}
