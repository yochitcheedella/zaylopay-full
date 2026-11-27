package com.zaylope.registration.common.exception;

public class PaymentOrderNotFoundException extends RuntimeException {
	public PaymentOrderNotFoundException() {
		super("Payment order not found.");
	}

	public PaymentOrderNotFoundException(String message) {
		super(message);
	}
}
