package com.zaylope.registration.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaylope.registration.verification.ApplicantVerificationChannel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class LoginOtpDeliveryStateServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	private ApplicantAuthChallengeRepository challengeRepository;
	private ApplicantAuthChallenge challenge;
	private LoginOtpDeliveryStateService service;

	@BeforeEach
	void setUp() {
		challengeRepository = mock(ApplicantAuthChallengeRepository.class);
		challenge = mock(ApplicantAuthChallenge.class);
		service = new LoginOtpDeliveryStateService(
				challengeRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
		when(challengeRepository.findByIssuanceIdForUpdate("issuance-1"))
				.thenReturn(Optional.of(challenge));
		when(challenge.getPurpose()).thenReturn(ApplicantAuthChallenge.Purpose.LOGIN);
		when(challenge.getChannel()).thenReturn(ApplicantVerificationChannel.EMAIL);
		when(challenge.getStatus()).thenReturn("ACTIVE");
		when(challenge.isConsumed()).thenReturn(false);
		when(challenge.isExpiredAt(NOW)).thenReturn(false);
		when(challenge.getExpiresAt()).thenReturn(NOW.plusSeconds(300));
	}

	@Test
	void onlyCurrentActiveUnexpiredMatchingChallengesCanBeDispatched() {
		Optional<Instant> expiration = service.findActiveUnexpiredChallengeExpiration(
				new LoginOtpDispatchEvent(
						"issuance-1",
						ApplicantVerificationChannel.EMAIL,
						"member@example.com",
						"123456"));

		assertEquals(Optional.of(NOW.plusSeconds(300)), expiration);
	}

	@Test
	void expiredChallengeCannotBeDispatched() {
		when(challenge.isExpiredAt(NOW)).thenReturn(true);

		assertTrue(service.findActiveUnexpiredChallengeExpiration(
				new LoginOtpDispatchEvent(
						"issuance-1",
						ApplicantVerificationChannel.EMAIL,
						"member@example.com",
						"123456"))
				.isEmpty());
	}

	@Test
	void mismatchedChannelCannotBeDispatched() {
		assertTrue(service.findActiveUnexpiredChallengeExpiration(
				new LoginOtpDispatchEvent(
						"issuance-1",
						ApplicantVerificationChannel.MOBILE,
						"+919876543210",
						"123456"))
				.isEmpty());
	}

	@Test
	void failedDeliveryInvalidatesOnlyAnActiveChallenge() {
		service.markDeliveryFailed("issuance-1");

		verify(challenge).deliveryFailed(NOW);
		verify(challengeRepository).saveAndFlush(challenge);
	}

	@Test
	void alreadyConsumedChallengeIsNotInvalidated() {
		when(challenge.isConsumed()).thenReturn(true);

		service.markDeliveryFailed("issuance-1");

		verify(challenge, never()).deliveryFailed(NOW);
		verify(challengeRepository, never()).saveAndFlush(challenge);
	}

	@Test
	void databaseOperationsUseShortNewTransactions() throws Exception {
		var lookup = LoginOtpDeliveryStateService.class.getMethod(
				"findActiveUnexpiredChallengeExpiration", LoginOtpDispatchEvent.class);
		var failureUpdate = LoginOtpDeliveryStateService.class.getMethod(
				"markDeliveryFailed", String.class);

		assertEquals(
				Propagation.REQUIRES_NEW,
				lookup.getAnnotation(Transactional.class).propagation());
		assertEquals(
				Propagation.REQUIRES_NEW,
				failureUpdate.getAnnotation(Transactional.class).propagation());
	}
}
