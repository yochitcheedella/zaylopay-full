package com.zaylope.registration.verification;
import java.time.Instant;
public record OtpDispatchResponse(String maskedDestination, long resendAfterSeconds, Instant serverTime, Instant expiresAt, Instant resendAvailableAt, String deliveryMode) {
    public OtpDispatchResponse(String maskedDestination, long resendAfterSeconds) { this(maskedDestination,resendAfterSeconds,null,null,null,"PROVIDER"); }
}
