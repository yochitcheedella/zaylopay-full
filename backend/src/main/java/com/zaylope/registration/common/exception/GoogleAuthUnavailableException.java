package com.zaylope.registration.common.exception;

public class GoogleAuthUnavailableException extends RuntimeException {
	public GoogleAuthUnavailableException() {
		super("Google Sign-In is currently unavailable. Please register or log in with your mobile number or email.");
	}
}
