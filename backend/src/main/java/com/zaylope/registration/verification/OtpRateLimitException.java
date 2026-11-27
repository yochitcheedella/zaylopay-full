package com.zaylope.registration.verification;

public class OtpRateLimitException extends RuntimeException {
	public enum Scope { IDENTIFIER, APPLICANT, IP, CHALLENGE }

	private final String code;
	private final long retryAfterSeconds;
	private final Scope scope;
	private final java.time.Instant serverTime;
	private final java.time.Instant retryAt;

	public OtpRateLimitException(String code, long retryAfterSeconds) {
		this(code, retryAfterSeconds, Scope.CHALLENGE, null, null);
	}

	public OtpRateLimitException(String code, long retryAfterSeconds, Scope scope,
			java.time.Instant serverTime, java.time.Instant retryAt) {
		this.code = code;
		this.retryAfterSeconds = retryAfterSeconds;
		this.scope = scope;
		this.serverTime = serverTime;
		this.retryAt = retryAt;
	}
	public Scope scope() { return scope; }
	public java.time.Instant serverTime() { return serverTime; }
	public java.time.Instant retryAt() { return retryAt; }

	public String code() {
		return code;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
