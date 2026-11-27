package com.zaylope.registration.verification;

public interface EmailOtpDelivery {
	default boolean isMock() { return false; }

	void sendCode(String emailAddress, String code);
}
