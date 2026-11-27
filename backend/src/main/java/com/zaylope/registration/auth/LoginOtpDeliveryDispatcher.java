package com.zaylope.registration.auth;

import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class LoginOtpDeliveryDispatcher {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoginOtpDeliveryDispatcher.class);

	private final LoginOtpDeliveryStateService deliveryStateService;
	private final MobileOtpDelivery mobileDelivery;
	private final EmailOtpDelivery emailDelivery;
	private final Clock clock;
	private final ConcurrentMap<String, Instant> dispatchedIssuances = new ConcurrentHashMap<>();

	public LoginOtpDeliveryDispatcher(
			LoginOtpDeliveryStateService deliveryStateService,
			MobileOtpDelivery mobileDelivery,
			EmailOtpDelivery emailDelivery,
			Clock clock) {
		this.deliveryStateService = deliveryStateService;
		this.mobileDelivery = mobileDelivery;
		this.emailDelivery = emailDelivery;
		this.clock = clock;
	}

	@Async("loginOtpExecutor")
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void dispatch(LoginOtpDispatchEvent event) {
		Optional<Instant> expiresAt = deliveryStateService
				.findActiveUnexpiredChallengeExpiration(event);
		if (expiresAt.isEmpty()) {
			return;
		}

		Instant now = clock.instant();
		dispatchedIssuances.entrySet().removeIf(entry -> !now.isBefore(entry.getValue()));
		if (!now.isBefore(expiresAt.get())
				|| dispatchedIssuances.putIfAbsent(event.issuanceId(), expiresAt.get()) != null) {
			return;
		}

		try {
			if (event.channel() == ApplicantVerificationChannel.MOBILE) {
				mobileDelivery.sendCode(event.destination(), event.code());
			} else {
				emailDelivery.sendCode(event.destination(), event.code());
			}
		} catch (RuntimeException failure) {
			try {
				deliveryStateService.markDeliveryFailed(event.issuanceId());
			} catch (RuntimeException stateFailure) {
				LOGGER.error("Could not invalidate failed login challenge for channel={}", event.channel());
			}
			LOGGER.warn("Login verification delivery failed for channel={}", event.channel());
		}
	}
}
