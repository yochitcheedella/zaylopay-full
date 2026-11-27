package com.zaylope.registration.verification;

public class ContactReauthenticationRequiredException extends RuntimeException {
    public ContactReauthenticationRequiredException() {
        super("Changing a verified contact requires re-authentication and proof of the new contact. This draft correction form cannot replace it.");
    }
}
