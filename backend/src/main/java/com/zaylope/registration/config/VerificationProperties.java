package com.zaylope.registration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.verification")
public record VerificationProperties(
		boolean mobileEnabled,
		boolean emailEnabled,
		String otpHashKey,
		Duration otpLifetime,
		Duration resendCooldown,
		Duration sendWindow,
		int maxSendsPerWindow,
		int maxAttempts) {

	public VerificationProperties {
		if (otpLifetime == null || otpLifetime.isNegative() || otpLifetime.isZero()
				|| resendCooldown == null || resendCooldown.isNegative()
				|| sendWindow == null || sendWindow.isNegative() || sendWindow.isZero()
				|| maxSendsPerWindow < 1 || maxAttempts < 1) {
			throw new IllegalArgumentException("Verification limits must be positive.");
		}
	}

	public boolean hasOtpHashKey() {
		return otpHashKey != null
				&& otpHashKey.getBytes(java.nio.charset.StandardCharsets.UTF_8).length >= 32;
	}
	@Override public String toString() { return "VerificationProperties[mobileEnabled=" + mobileEnabled + ", emailEnabled=" + emailEnabled + ", otpHashKey=REDACTED]"; }
}
