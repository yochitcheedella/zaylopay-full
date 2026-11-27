package com.zaylope.registration.common.exception;

public class PaymentRequiredForApprovalException extends RuntimeException {
	public PaymentRequiredForApprovalException() {
		super("Application cannot be approved before payment of ₹599 is confirmed.");
	}

	public PaymentRequiredForApprovalException(String message) {
		super(message);
	}
}
