package com.zaylope.registration.verification;

public class ContactUpdateConflictException extends RuntimeException {
    public ContactUpdateConflictException() {
        super("Contact details changed during this request. Refresh your details and retry.");
    }
}
