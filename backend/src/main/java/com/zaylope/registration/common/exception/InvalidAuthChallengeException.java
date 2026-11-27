package com.zaylope.registration.common.exception;

public class InvalidAuthChallengeException extends RuntimeException {
	public InvalidAuthChallengeException() {
		super("The verification code is invalid or expired.");
	}
}
