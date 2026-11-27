package com.zaylope.registration.session;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.config.ApplicantSessionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicantSessionServiceTest {

	@Mock
	private ApplicantAccessSessionRepository sessionRepository;

	private ApplicantSessionService applicantSessionService;

	@BeforeEach
	void setUp() {
		applicantSessionService = new ApplicantSessionService(
				sessionRepository,
				new ApplicantSessionProperties("ZAYLOPE_APPLICANT", Duration.ofMinutes(30), false),
				Clock.fixed(Instant.parse("2026-11-17T16:30:00Z"), ZoneOffset.UTC));
	}

	@Test
	void storesOnlyTokenHashAndResolvesTheSessionByHash() {
		when(sessionRepository.save(any(ApplicantAccessSession.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		when(sessionRepository.findActiveApplicantId(anyString(), any(Instant.class)))
				.thenReturn(Optional.of(42L));

		String token = applicantSessionService.createFor(
				new Applicant("Rahul Kumar", "+919876543210", "rahul@example.com"));
		ArgumentCaptor<ApplicantAccessSession> saved = ArgumentCaptor.forClass(ApplicantAccessSession.class);
		verify(sessionRepository).save(saved.capture());
		assertNotEquals(token, tokenHash(saved.getValue()));
		assertEquals(43, token.length());
		assertEquals(Optional.of(42L), applicantSessionService.findActiveApplicantId(token));
		ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
		verify(sessionRepository).findActiveApplicantId(hash.capture(), any(Instant.class));
		assertEquals(tokenHash(saved.getValue()), hash.getValue());
	}

	@Test
	void rejectsTokensWithUnexpectedLengthWithoutDatabaseLookup() {
		assertTrue(applicantSessionService.findActiveApplicantId("too-short").isEmpty());
	}

	private static String tokenHash(ApplicantAccessSession session) {
		try {
			java.lang.reflect.Field field = ApplicantAccessSession.class.getDeclaredField("tokenHash");
			field.setAccessible(true);
			return (String) field.get(session);
		} catch (ReflectiveOperationException exception) {
			throw new AssertionError(exception);
		}
	}
}
