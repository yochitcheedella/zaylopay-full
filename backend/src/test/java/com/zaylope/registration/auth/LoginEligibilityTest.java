package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.affiliate.MembershipActivationPolicy;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.InvalidAuthChallengeException;
import com.zaylope.registration.config.VerificationProperties;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.*;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginEligibilityTest {
	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");
	private final ApplicantRepository applicants = mock(ApplicantRepository.class);
	private final ApplicantAuthChallengeRepository challenges = mock(ApplicantAuthChallengeRepository.class);
	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	private final EmailOtpDelivery email = mock(EmailOtpDelivery.class);
	private final MobileOtpDelivery mobile = mock(MobileOtpDelivery.class);
	private final ApplicantSessionService sessions = mock(ApplicantSessionService.class);
	private final VerificationProperties properties = new VerificationProperties(true, true,
			"isolated-test-hash-secret-at-least-32-bytes", Duration.ofMinutes(5),
			Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
	private final ApplicantAuthChallengeService service = new ApplicantAuthChallengeService(applicants,
			mock(AffiliateMembershipRepository.class), new MembershipActivationPolicy(), challenges,
			events, properties, mobile, email, new ApplicantOtpHasher(properties), mock(OtpAbuseGuard.class),
			sessions, Clock.fixed(NOW, ZoneOffset.UTC));

	@ParameterizedTest
	@ValueSource(strings = {"Other@custom-domain.dev", "member@custom-domain.dev", "Member@sub.custom-domain.dev"})
	void rejectsMismatchedRepositoryResultEvenUnderUnexpectedDatabaseCollation(String identifier) {
		Applicant applicant = member();
		when(applicants.findByEmailAndEmailVerifiedAtIsNotNull(identifier)).thenReturn(Optional.of(applicant));
		service.requestLoginOtp(identifier);
		verify(challenges, never()).saveAndFlush(any());
		verifyNoInteractions(events, email, mobile, sessions);
	}

	@Test
	void rejectsMismatchedMobileRepositoryResult() {
		when(applicants.findByMobileNumberAndMobileVerifiedTrue("+919876543211"))
				.thenReturn(Optional.of(member()));
		service.requestLoginOtp("9876543211");
		verify(challenges, never()).saveAndFlush(any());
		verifyNoInteractions(events, email, mobile, sessions);
	}

	@Test
	void eligibleAndUnknownResponsesMatchAndOnlyEligibleIssuanceReachesTestProvider() {
		Applicant applicant = member();
		when(applicants.findByEmailAndEmailVerifiedAtIsNotNull(applicant.getEmail())).thenReturn(Optional.of(applicant));
		var eligible = service.requestLoginOtp(applicant.getEmail());
		var unknown = service.requestLoginOtp("unknown@custom-domain.dev");
		assertEquals(eligible.message(), unknown.message());
		assertEquals(eligible.accepted(), unknown.accepted());
		assertEquals(eligible.expiresAt(), unknown.expiresAt());
		assertEquals(eligible.resendAvailableAt(), unknown.resendAvailableAt());
		var captured = org.mockito.ArgumentCaptor.forClass(Object.class);
		verify(events, times(1)).publishEvent(captured.capture());
		LoginOtpDispatchEvent event = (LoginOtpDispatchEvent) captured.getValue();
		var states = mock(LoginOtpDeliveryStateService.class);
		when(states.findActiveUnexpiredChallengeExpiration(event)).thenReturn(Optional.of(NOW.plusSeconds(300)));
		new LoginOtpDeliveryDispatcher(states, mobile, email, Clock.fixed(NOW, ZoneOffset.UTC)).dispatch(event);
		verify(email, times(1)).sendCode(eq(applicant.getEmail()), anyString());
		verifyNoInteractions(mobile, sessions);
		verify(challenges, times(1)).saveAndFlush(any());
	}

	@Test
	void verifiesRealGeneratedCodeRotatesSessionAndRejectsReplayWrongIdentifierWrongCodeAndExpiry() {
		Applicant applicant = member();
		when(applicants.findByEmailAndEmailVerifiedAtIsNotNull(applicant.getEmail())).thenReturn(Optional.of(applicant));
		service.requestLoginOtp(applicant.getEmail());
		var eventCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
		verify(events).publishEvent(eventCaptor.capture());
		LoginOtpDispatchEvent event = (LoginOtpDispatchEvent) eventCaptor.getValue();
		var challengeCaptor = org.mockito.ArgumentCaptor.forClass(ApplicantAuthChallenge.class);
		verify(challenges).saveAndFlush(challengeCaptor.capture());
		ApplicantAuthChallenge challenge = challengeCaptor.getValue();
		when(challenges.findByIssuanceIdForUpdate(event.issuanceId())).thenReturn(Optional.of(challenge));
		when(applicants.findByIdForUpdate(71L)).thenReturn(Optional.of(applicant));
		assertThrows(InvalidAuthChallengeException.class,
				() -> service.verifyLoginOtp(event.issuanceId(), "different@custom-domain.dev", event.code(), null));
		String wrong = event.code().equals("000000") ? "111111" : "000000";
		assertThrows(InvalidAuthChallengeException.class,
				() -> service.verifyLoginOtp(event.issuanceId(), applicant.getEmail(), wrong, null));
		ReflectionTestUtils.setField(challenge, "expiresAt", NOW.minusSeconds(1));
		assertThrows(InvalidAuthChallengeException.class,
				() -> service.verifyLoginOtp(event.issuanceId(), applicant.getEmail(), event.code(), null));
		verifyNoInteractions(sessions);
		ReflectionTestUtils.setField(challenge, "expiresAt", NOW.plusSeconds(300));
		var result = service.verifyLoginOtp(event.issuanceId(), applicant.getEmail(), event.code(), "old-session");
		assertEquals("Test Member", result.applicant().getFullName());
		verify(sessions).rotateSession(eq(applicant), any(), eq("old-session"));
		assertThrows(InvalidAuthChallengeException.class,
				() -> service.verifyLoginOtp(event.issuanceId(), applicant.getEmail(), event.code(), null));
	}

	private Applicant member() {
		Applicant applicant = new Applicant("Test Member", "+919876543210", "Member@custom-domain.dev");
		ReflectionTestUtils.setField(applicant, "applicantId", 71L);
		applicant.markEmailVerified(NOW, Applicant.EMAIL_METHOD_OTP);
		applicant.markMobileVerified(NOW);
		return applicant;
	}
}
