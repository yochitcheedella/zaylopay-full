package com.zaylope.registration.common.exception;

public class PrivateDocumentStorageException extends RuntimeException {

	public PrivateDocumentStorageException(Throwable cause) {
		super("Private document storage could not complete the request.", cause);
	}
}
