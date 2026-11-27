package com.zaylope.registration.common.validation;

import java.time.Duration;
import java.util.Set;
import org.springframework.core.io.DefaultResourceLoader;

public final class EmailContactPolicyFixtures {

	private EmailContactPolicyFixtures() {}

	public static EmailPolicyProperties properties(boolean allowlist, boolean dns) {
		return new EmailPolicyProperties(
				allowlist,
				Set.of("gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com",
						"hotmail.com", "live.com", "icloud.com", "proton.me", "protonmail.com", "rediffmail.com"),
				true,
				"classpath:validation/disposable-email-domains.conf",
				dns,
				Duration.ofMillis(50),
				Duration.ofMinutes(5),
				8);
	}

	public static EmailContactPolicy policy(boolean allowlist, boolean dns) {
		return new EmailContactPolicy(properties(allowlist, dns), new DefaultResourceLoader());
	}
}
