package com.zaylope.registration.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

class LoginOtpDeliveryDispatcherTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	private LoginOtpDeliveryStateService deliveryStateService;
	private MobileOtpDelivery mobileDelivery;
	private EmailOtpDelivery emailDelivery;
	private LoginOtpDeliveryDispatcher dispatcher;

	@BeforeEach
	void setUp() {
		deliveryStateService = org.mockito.Mockito.mock(LoginOtpDeliveryStateService.class);
		mobileDelivery = org.mockito.Mockito.mock(MobileOtpDelivery.class);
		emailDelivery = org.mockito.Mockito.mock(EmailOtpDelivery.class);
		dispatcher = new LoginOtpDeliveryDispatcher(
				deliveryStateService,
				mobileDelivery,
				emailDelivery,
				Clock.fixed(NOW, ZoneOffset.UTC));
		when(deliveryStateService.findActiveUnexpiredChallengeExpiration(any()))
				.thenReturn(Optional.of(NOW.plusSeconds(300)));
	}

	@Test
	void dispatchesRegisteredEmailOtp() {
		LoginOtpDispatchEvent event = event("email-issuance", ApplicantVerificationChannel.EMAIL);

		dispatcher.dispatch(event);

		verify(emailDelivery).sendCode("member@example.com", "123456");
		verify(mobileDelivery, never()).sendCode(any(), any());
	}

	@Test
	void dispatchesRegisteredMobileOtp() {
		LoginOtpDispatchEvent event = event("mobile-issuance", ApplicantVerificationChannel.MOBILE);

		dispatcher.dispatch(event);

		verify(mobileDelivery).sendCode("+919876543210", "123456");
		verify(emailDelivery, never()).sendCode(any(), any());
	}

	@Test
	void duplicateEventDoesNotSendDuplicateOtp() {
		LoginOtpDispatchEvent event = event("same-issuance", ApplicantVerificationChannel.EMAIL);

		dispatcher.dispatch(event);
		dispatcher.dispatch(event);

		verify(emailDelivery, times(1)).sendCode("member@example.com", "123456");
	}

	@Test
	void expiredOrIneligibleChallengeIsNotDispatched() {
		when(deliveryStateService.findActiveUnexpiredChallengeExpiration(any())).thenReturn(Optional.empty());

		dispatcher.dispatch(event("expired-issuance", ApplicantVerificationChannel.EMAIL));

		verify(emailDelivery, never()).sendCode(any(), any());
		verify(mobileDelivery, never()).sendCode(any(), any());
	}

	@Test
	void providerFailureMarksChallengeFailedWithoutRetryingDelivery() {
		LoginOtpDispatchEvent event = event("failed-issuance", ApplicantVerificationChannel.EMAIL);
		org.mockito.Mockito.doThrow(new IllegalStateException("provider unavailable"))
				.when(emailDelivery)
				.sendCode(event.destination(), event.code());

		dispatcher.dispatch(event);
		dispatcher.dispatch(event);

		verify(deliveryStateService).markDeliveryFailed(event.issuanceId());
		verify(emailDelivery, times(1)).sendCode(event.destination(), event.code());
	}

	@Test
	void listenerRunsAfterCommitWithoutHoldingTransaction() throws Exception {
		var method = LoginOtpDeliveryDispatcher.class.getMethod("dispatch", LoginOtpDispatchEvent.class);
		Transactional transaction = method.getAnnotation(Transactional.class);
		TransactionalEventListener eventListener = method.getAnnotation(TransactionalEventListener.class);
		Async async = method.getAnnotation(Async.class);

		assertEquals(Propagation.NOT_SUPPORTED, transaction.propagation());
		assertEquals(TransactionPhase.AFTER_COMMIT, eventListener.phase());
		assertEquals("loginOtpExecutor", async.value());
	}

	private LoginOtpDispatchEvent event(String issuanceId, ApplicantVerificationChannel channel) {
		String destination = channel == ApplicantVerificationChannel.EMAIL
				? "member@example.com"
				: "+919876543210";
		return new LoginOtpDispatchEvent(issuanceId, channel, destination, "123456");
	}
}
