package com.zaylope.registration.common.exception;

public class InvalidRegistrationInputException extends RuntimeException {
    private final String field;

    public InvalidRegistrationInputException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
