package com.zaylope.registration.common.exception;

public class ApplicationNotSubmittedException extends RuntimeException {
	public ApplicationNotSubmittedException() {
		super("Please complete and submit your application before initiating payment.");
	}
}
