package com.zaylope.registration.common.exception;

public class InvalidPaymentSignatureException extends RuntimeException {
	public InvalidPaymentSignatureException() {
		super("Payment signature verification failed. The payment authenticity could not be confirmed.");
	}
}
