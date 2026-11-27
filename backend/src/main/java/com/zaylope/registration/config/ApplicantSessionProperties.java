package com.zaylope.registration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "applicant.session")
public record ApplicantSessionProperties(
		String cookieName,
		Duration lifetime,
		boolean secureCookie,
		String onboardingCookieName,
		Duration onboardingLifetime) {

	public ApplicantSessionProperties(String cookieName, Duration lifetime, boolean secureCookie) {
		this(cookieName, lifetime, secureCookie, "ZAYLOPE_ONBOARDING", Duration.ofMinutes(15));
	}

	@ConstructorBinding
	public ApplicantSessionProperties {
		if (cookieName == null || cookieName.isBlank()) {
			cookieName = "ZAYLOPE_APPLICANT";
		}
		if (lifetime == null || lifetime.isNegative() || lifetime.isZero()) {
			lifetime = Duration.ofMinutes(30);
		}
		if (onboardingCookieName == null || onboardingCookieName.isBlank()) {
			onboardingCookieName = "ZAYLOPE_ONBOARDING";
		}
		if (onboardingLifetime == null || onboardingLifetime.isNegative() || onboardingLifetime.isZero()) {
			onboardingLifetime = Duration.ofMinutes(15);
		}
	}
}
