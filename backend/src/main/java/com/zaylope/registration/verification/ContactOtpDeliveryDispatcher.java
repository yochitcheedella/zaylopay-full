package com.zaylope.registration.verification;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ContactOtpDeliveryDispatcher {
    private final ContactOtpDeliveryStateService states;
    private final MobileOtpDelivery mobile;
    private final EmailOtpDelivery email;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> dispatched = new ConcurrentHashMap<>();
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ContactOtpDeliveryDispatcher.class);
    public ContactOtpDeliveryDispatcher(ContactOtpDeliveryStateService states, MobileOtpDelivery mobile, EmailOtpDelivery email, Clock clock) {
        this.states = states; this.mobile = mobile; this.email = email; this.clock = clock;
    }
    @Async("loginOtpExecutor")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ContactOtpDispatchEvent event) {
        var expiration = states.expiration(event);
        if (expiration.isEmpty()) return;
        Instant now = clock.instant();
        dispatched.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
        if (!now.isBefore(expiration.get()) || dispatched.putIfAbsent(event.issuanceId(), expiration.get()) != null) return;
        try {
            if (event.channel() == ApplicantVerificationChannel.MOBILE) mobile.sendCode(event.destination(), event.code());
            else email.sendCode(event.destination(), event.code());
        } catch (RuntimeException failure) {
            try { states.failed(event); }
            catch (RuntimeException stateFailure) { LOGGER.error("Could not invalidate failed contact challenge"); }
            LOGGER.warn("Contact verification delivery failed for channel={}", event.channel());
        }
    }
}
