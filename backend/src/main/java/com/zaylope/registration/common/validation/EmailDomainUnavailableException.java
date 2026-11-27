package com.zaylope.registration.common.validation;

public class EmailDomainUnavailableException extends RuntimeException {
    public EmailDomainUnavailableException() {
        super("Email domain checks are temporarily unavailable. Your details are unchanged; please retry.");
    }
}
