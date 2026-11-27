package com.zaylope.registration.verification;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactOtpDeliveryTest {
    private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
    @Test
    void failedDeliveryInvalidatesChallengeAndNeverLogsOrReturnsSecrets() {
        var states = mock(ContactOtpDeliveryStateService.class);
        var mobile = mock(MobileOtpDelivery.class);
        var email = mock(EmailOtpDelivery.class);
        var event = new ContactOtpDispatchEvent(71L, ApplicantVerificationChannel.EMAIL, "issue", "member@gmail.com", "123456");
        when(states.expiration(event)).thenReturn(Optional.of(NOW.plusSeconds(300)));
        doThrow(new IllegalStateException("provider failed")).when(email).sendCode(anyString(), anyString());
        var dispatcher = new ContactOtpDeliveryDispatcher(states, mobile, email, Clock.fixed(NOW, ZoneOffset.UTC));
        dispatcher.dispatch(event);
        dispatcher.dispatch(event);
        verify(email, times(1)).sendCode(anyString(), anyString());
        verify(states).failed(event);
        verifyNoInteractions(mobile);
        assertFalse(event.toString().contains(event.code()));
        assertFalse(event.toString().contains(event.destination()));
    }
    @Test
    void staleAndExpiredIssuancesAreNeverDelivered() {
        var states = mock(ContactOtpDeliveryStateService.class);
        var mobile = mock(MobileOtpDelivery.class);
        var email = mock(EmailOtpDelivery.class);
        var event = new ContactOtpDispatchEvent(71L, ApplicantVerificationChannel.EMAIL, "issue", "member@gmail.com", "123456");
        when(states.expiration(event))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(NOW.minusSeconds(1)));
        var dispatcher = new ContactOtpDeliveryDispatcher(states, mobile, email, Clock.fixed(NOW, ZoneOffset.UTC));
        dispatcher.dispatch(event); dispatcher.dispatch(event);
        verifyNoInteractions(mobile, email);
    }
}
