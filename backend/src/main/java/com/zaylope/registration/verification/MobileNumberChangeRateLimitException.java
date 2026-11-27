package com.zaylope.registration.verification;

public class MobileNumberChangeRateLimitException extends RuntimeException {

	private final long retryAfterSeconds;

	public MobileNumberChangeRateLimitException(long retryAfterSeconds) {
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
