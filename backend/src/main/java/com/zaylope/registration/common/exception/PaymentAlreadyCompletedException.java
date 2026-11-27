package com.zaylope.registration.common.exception;

public class PaymentAlreadyCompletedException extends RuntimeException {
	public PaymentAlreadyCompletedException() {
		super("Payment of ₹599 has already been completed for this application.");
	}
}
