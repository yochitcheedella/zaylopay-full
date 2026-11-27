package com.zaylope.registration.common.exception;

public class AdminUnauthorizedException extends RuntimeException {
	public AdminUnauthorizedException() {
		super("Administrator authentication required.");
	}

	public AdminUnauthorizedException(String message) {
		super(message);
	}
}
