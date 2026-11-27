package com.zaylope.registration.auth;

import com.zaylope.registration.config.ApplicantSessionProperties;
import com.zaylope.registration.session.ApplicantSessionService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantOnboardingSessionService {

	private static final int TOKEN_BYTES = 32;
	private static final int TOKEN_LENGTH = 43;

	private final ApplicantOnboardingSessionRepository repository;
	private final ApplicantSessionProperties properties;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public ApplicantOnboardingSessionService(
			ApplicantOnboardingSessionRepository repository,
			ApplicantSessionProperties properties,
			Clock clock) {
		this.repository = repository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public String create(
			ApplicantOnboardingSession.FlowType flowType,
			GoogleTokenVerifierService.GoogleUserPayload payload,
			Long targetApplicantId) {
		byte[] tokenBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		Instant now = clock.instant();
		ApplicantOnboardingSession session = new ApplicantOnboardingSession(
				ApplicantSessionService.hashToken(token),
				flowType,
				payload.sub(),
				payload.email(),
				payload.emailVerified(),
				payload.hostedDomain(),
				payload.name(),
				payload.pictureUrl(),
				targetApplicantId,
				now,
				now.plus(properties.onboardingLifetime()));
		repository.save(session);
		return token;
	}

	@Transactional(readOnly = true)
	public Optional<ApplicantOnboardingSession> findActive(String token) {
		if (token == null || token.length() != TOKEN_LENGTH) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return repository.findByTokenHash(ApplicantSessionService.hashToken(token))
				.filter(session -> session.isActiveAt(now));
	}

	@Transactional
	public Optional<ApplicantOnboardingSession> lockActiveForUpdate(String token) {
		if (token == null || token.length() != TOKEN_LENGTH) {
			return Optional.empty();
		}
		Instant now = clock.instant();
		return repository.findByTokenHashForUpdate(ApplicantSessionService.hashToken(token))
				.filter(session -> session.isActiveAt(now));
	}

	@Transactional
	public void revoke(String token) {
		if (token == null || token.length() != TOKEN_LENGTH) {
			return;
		}
		repository.findByTokenHashForUpdate(ApplicantSessionService.hashToken(token))
				.ifPresent(session -> {
					session.revoke(clock.instant());
					repository.save(session);
				});
	}

	@Transactional
	public int deleteExpiredSessions() {
		return repository.deleteExpiredSessions(clock.instant());
	}
}
