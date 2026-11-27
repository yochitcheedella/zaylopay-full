package com.zaylope.registration.verification;

public interface MobileOtpDelivery {
	default boolean isMock() { return false; }

	void sendCode(String mobileNumber, String code);
}
