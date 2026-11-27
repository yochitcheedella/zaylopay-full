package com.zaylope.registration.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development-only delivery sink. The code is emitted to the backend console, never returned by an API. */
public final class DevelopmentOtpDelivery implements MobileOtpDelivery, EmailOtpDelivery {
    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentOtpDelivery.class);

    @Override public boolean isMock() { return true; }
    @Override public void sendCode(String destination, String code) {
        String channel = destination.contains("@") ? "EMAIL" : "MOBILE";
        LOGGER.info("LOCAL DEVELOPMENT OTP channel={} code={}", channel, code);
    }

}
