package com.zaylope.registration.common.exception;

public class OnboardingConsentRequiredException extends RuntimeException {

	public OnboardingConsentRequiredException() {
		super("Accept the application declaration and privacy consent before submitting.");
	}
}
