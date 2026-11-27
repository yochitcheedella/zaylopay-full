package com.zaylope.registration.verification;
import java.time.Instant;
public record VerificationStateResponse(Instant serverTime, String deliveryMode, ChannelState mobile, ChannelState email, boolean contactVerified) {
    public VerificationStateResponse(Instant serverTime, String deliveryMode, ChannelState mobile, ChannelState email) {
        this(serverTime, deliveryMode, mobile, email, (mobile != null && mobile.verified()) || (email != null && email.verified()));
    }
    public record ChannelState(String maskedDestination, boolean verified, boolean enabled, Instant expiresAt, Instant resendAvailableAt, String challengeStatus) {}
}
