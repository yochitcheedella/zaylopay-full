package com.zaylope.registration.admin;

import com.zaylope.registration.common.exception.AdminLoginRateLimitException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thread-safe sliding window rate limiter for admin authentication.
 * Enforces per-IP and per-username cooldowns to prevent brute-force attacks.
 */
@Component
public class AdminLoginRateLimiter {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminLoginRateLimiter.class);

	private final int maxAttempts;
	private final Duration cooldownDuration;

	private final Map<String, AttemptRecord> ipAttempts = new ConcurrentHashMap<>();
	private final Map<String, AttemptRecord> usernameAttempts = new ConcurrentHashMap<>();

	public AdminLoginRateLimiter(
			@Value("${app.security.admin.max-login-attempts:5}") int maxAttempts,
			@Value("${app.security.admin.cooldown-minutes:15}") int cooldownMinutes) {
		this.maxAttempts = Math.max(1, maxAttempts);
		this.cooldownDuration = Duration.ofMinutes(Math.max(1, cooldownMinutes));
	}

	public void checkAllowed(String clientIp, String username) {
		Instant now = Instant.now();
		checkEntry(clientIp, ipAttempts, now, "IP address");
		if (username != null && !username.isBlank()) {
			checkEntry(username.trim().toLowerCase(), usernameAttempts, now, "username");
		}
	}

	public void recordFailure(String clientIp, String username) {
		Instant now = Instant.now();
		incrementEntry(clientIp, ipAttempts, now);
		if (username != null && !username.isBlank()) {
			incrementEntry(username.trim().toLowerCase(), usernameAttempts, now);
		}
	}

	public void recordSuccess(String clientIp, String username) {
		if (clientIp != null) {
			ipAttempts.remove(clientIp);
		}
		if (username != null && !username.isBlank()) {
			usernameAttempts.remove(username.trim().toLowerCase());
		}
	}

	public void resetAll() {
		ipAttempts.clear();
		usernameAttempts.clear();
	}

	private void checkEntry(String key, Map<String, AttemptRecord> map, Instant now, String type) {
		if (key == null || key.isBlank()) return;
		AttemptRecord record = map.get(key);
		if (record != null) {
			if (record.isExpired(now, cooldownDuration)) {
				map.remove(key);
				return;
			}
			if (record.count >= maxAttempts) {
				long secondsLeft = cooldownDuration.minus(Duration.between(record.firstAttempt, now)).toSeconds();
				long safeSeconds = Math.max(1, secondsLeft);
				LOGGER.warn("Admin login rate limit triggered for {} '{}'. Cooldown remaining: {} seconds", type, key, safeSeconds);
				throw new AdminLoginRateLimitException(
						"Too many failed login attempts. Please wait " + Math.max(1, (safeSeconds + 59) / 60) + " minutes before retrying.",
						safeSeconds);
			}
		}
	}

	private void incrementEntry(String key, Map<String, AttemptRecord> map, Instant now) {
		if (key == null || key.isBlank()) return;
		map.compute(key, (k, existing) -> {
			if (existing == null || existing.isExpired(now, cooldownDuration)) {
				return new AttemptRecord(1, now);
			}
			return new AttemptRecord(existing.count + 1, existing.firstAttempt);
		});
	}

	private static class AttemptRecord {
		final int count;
		final Instant firstAttempt;

		AttemptRecord(int count, Instant firstAttempt) {
			this.count = count;
			this.firstAttempt = firstAttempt;
		}

		boolean isExpired(Instant now, Duration cooldown) {
			return Duration.between(firstAttempt, now).compareTo(cooldown) > 0;
		}
	}
}
