package com.zaylope.registration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(Duration pendingReservationTtl) {

	public RegistrationProperties {
		if (pendingReservationTtl == null || pendingReservationTtl.isNegative() || pendingReservationTtl.isZero()) {
			pendingReservationTtl = Duration.ofMinutes(30);
		}
	}
}
