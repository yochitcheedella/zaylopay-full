package com.zaylope.registration.common.exception;

public class OnboardingSessionException extends RuntimeException {
	private final String code;

	public OnboardingSessionException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}
}
