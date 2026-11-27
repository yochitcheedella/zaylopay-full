package com.zaylope.registration.common.exception;

public class AdminLoginRateLimitException extends RuntimeException {

	private final long retryAfterSeconds;

	public AdminLoginRateLimitException(String message, long retryAfterSeconds) {
		super(message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}
}
