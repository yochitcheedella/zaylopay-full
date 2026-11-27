package com.zaylope.registration.session;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.config.ApplicantSessionProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicantSessionService {

	private static final int TOKEN_BYTES = 32;
	private static final int TOKEN_LENGTH = 43;

	private final ApplicantAccessSessionRepository sessionRepository;
	private final ApplicantSessionProperties properties;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public ApplicantSessionService(
			ApplicantAccessSessionRepository sessionRepository,
			ApplicantSessionProperties properties,
			Clock clock) {
		this.sessionRepository = sessionRepository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public String createFor(Applicant applicant) {
		ApplicantSessionScope scope = applicant.isContactVerified()
				? ApplicantSessionScope.AUTHENTICATED
				: ApplicantSessionScope.ONBOARDING;
		return createFor(applicant, scope);
	}

	@Transactional
	public String createFor(Applicant applicant, ApplicantSessionScope scope) {
		byte[] tokenBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		Instant now = clock.instant();
		sessionRepository.save(new ApplicantAccessSession(
				applicant,
				hashToken(token),
				scope != null ? scope : ApplicantSessionScope.ONBOARDING,
				now,
				now.plus(properties.lifetime())));
		return token;
	}

	@Transactional
	public String rotateSession(Applicant applicant, ApplicantSessionScope scope, String previousToken) {
		if (previousToken != null && !previousToken.isBlank()) {
			revoke(previousToken);
		}
		return createFor(applicant, scope);
	}

	@Transactional(readOnly = true)
	public Optional<Long> findActiveApplicantId(String token) {
		if (token == null || token.length() != TOKEN_LENGTH) {
			return Optional.empty();
		}
		return sessionRepository.findActiveApplicantId(hashToken(token), clock.instant());
	}

	@Transactional(readOnly = true)
	public Optional<ApplicantAccessSessionRepository.ActiveSessionProjection> findActiveSession(String token) {
		if (token == null || token.length() != TOKEN_LENGTH) {
			return Optional.empty();
		}
		return sessionRepository.findActiveSession(hashToken(token), clock.instant());
	}

	@Transactional
	public int deleteExpiredSessions() {
		return sessionRepository.deleteExpiredSessions(clock.instant());
	}

	@Transactional
	public void revoke(String token) {
		if (token == null || token.length() != TOKEN_LENGTH) {
			return;
		}
		sessionRepository.findByTokenHash(hashToken(token)).ifPresent(session -> {
			session.revoke(clock.instant());
			sessionRepository.save(session);
		});
	}

	@Transactional
	public int revokeAll(long applicantId) {
		return sessionRepository.revokeAllByApplicantId(applicantId, clock.instant());
	}

	public void issueSessionCookie(HttpServletResponse response, String token) {
		ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), token)
				.httpOnly(true)
				.secure(properties.secureCookie())
				.sameSite("Lax")
				.path("/")
				.maxAge(properties.lifetime())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearSessionCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(properties.cookieName(), "")
				.httpOnly(true)
				.secure(properties.secureCookie())
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ZERO)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void issueOnboardingCookie(HttpServletResponse response, String token) {
		ResponseCookie cookie = ResponseCookie.from(properties.onboardingCookieName(), token)
				.httpOnly(true)
				.secure(properties.secureCookie())
				.sameSite("Lax")
				.path("/")
				.maxAge(properties.onboardingLifetime())
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public void clearOnboardingCookie(HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(properties.onboardingCookieName(), "")
				.httpOnly(true)
				.secure(properties.secureCookie())
				.sameSite("Lax")
				.path("/")
				.maxAge(Duration.ZERO)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	public String extractApplicantToken(HttpServletRequest request) {
		return extractCookie(request, properties.cookieName());
	}

	public String extractOnboardingToken(HttpServletRequest request) {
		return extractCookie(request, properties.onboardingCookieName());
	}

	public static String extractCookie(HttpServletRequest request, String name) {
		if (request.getCookies() == null || name == null) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (name.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	public static String hashToken(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available", exception);
		}
	}
}
