package com.zaylope.registration.common.exception;

public class InvalidApplicantDocumentException extends RuntimeException {

	public InvalidApplicantDocumentException() {
		super("Select a valid PDF, JPG, JPEG, or PNG document no larger than 5 MB.");
	}

	public InvalidApplicantDocumentException(Throwable cause) {
		super("Select a valid PDF, JPG, JPEG, or PNG document no larger than 5 MB.", cause);
	}
}
