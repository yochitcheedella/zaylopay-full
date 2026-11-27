package com.zaylope.registration.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.zaylope.registration.common.validation.ApplicantInputValidator;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Cryptographic Google ID-token verifier service.
 * Verifies RSA signatures against Google's public certificates, audience (Client ID),
 * issuer, expiration, required sub/email claims, email_verified, and hosted domain (hd).
 */
@Service
public class GoogleTokenVerifierService {

	private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifierService.class);
	private static final Set<String> CONSUMER_GOOGLE_DOMAINS = Set.of("gmail.com", "googlemail.com");
	private static final Set<String> TRUSTED_ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	private final String googleClientId;
	private final GoogleIdTokenVerifier verifier;

	@Autowired
	public GoogleTokenVerifierService(@Value("${google.client-id:}") String googleClientId) {
		this.googleClientId = googleClientId != null ? googleClientId.trim() : "";
		if (this.googleClientId.isBlank()) {
			this.verifier = null;
			log.warn("Google Sign-In is disabled: google.client-id is not configured.");
		} else {
			this.verifier = new GoogleIdTokenVerifier.Builder(
					new NetHttpTransport(),
					GsonFactory.getDefaultInstance())
					.setAudience(Collections.singletonList(this.googleClientId))
					.build();
		}
	}

	GoogleTokenVerifierService(String googleClientId, GoogleIdTokenVerifier verifier) {
		this.googleClientId = googleClientId != null ? googleClientId.trim() : "";
		this.verifier = verifier;
	}

	public record GoogleUserPayload(
			String sub,
			String email,
			boolean emailVerified,
			String name,
			String pictureUrl,
			String hostedDomain) {

		public GoogleUserPayload(String sub, String email, boolean emailVerified, String name, String pictureUrl) {
			this(sub, email, emailVerified, name, pictureUrl, null);
		}
	}

	public boolean isConfigured() {
		return !googleClientId.isBlank() && verifier != null;
	}

	public Optional<GoogleUserPayload> verifyToken(String idToken) {
		if (!isConfigured()) {
			log.warn("Google token verification rejected: google.client-id is not configured");
			return Optional.empty();
		}
		if (idToken == null || idToken.isBlank()) {
			log.warn("Google token verification rejected: empty token");
			return Optional.empty();
		}

		try {
			GoogleIdToken token = verifier.verify(idToken.trim());
			if (token == null) {
				log.warn("Google ID token verification failed: signature invalid, audience mismatch, or token expired");
				return Optional.empty();
			}

			Payload payload = token.getPayload();

			String issuer = payload.getIssuer();
			if (issuer == null || !TRUSTED_ISSUERS.contains(issuer)) {
				log.warn("Google token rejected: untrusted issuer '{}'", issuer);
				return Optional.empty();
			}

			if (!payload.getAudienceAsList().contains(googleClientId)) {
				log.warn("Google token rejected: audience mismatch");
				return Optional.empty();
			}

			String sub = payload.getSubject();
			if (sub == null || sub.isBlank()) {
				log.warn("Google token rejected: missing sub claim");
				return Optional.empty();
			}

			String email = payload.getEmail();
			if (email == null || email.isBlank() || !email.contains("@")) {
				log.warn("Google token rejected: missing or invalid email claim");
				return Optional.empty();
			}

			String normalizedEmail = ApplicantInputValidator.normalizeEmail(email);
			if (normalizedEmail == null) {
				log.warn("Google token rejected: malformed email claim");
				return Optional.empty();
			}
			boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
			String name = payload.get("name") instanceof String s ? s.trim() : null;
			String pictureUrl = payload.get("picture") instanceof String s ? s.trim() : null;
			String hostedDomain = payload.getHostedDomain() != null
					? payload.getHostedDomain().trim().toLowerCase(Locale.ROOT)
					: null;

			return Optional.of(new GoogleUserPayload(sub.trim(), normalizedEmail, emailVerified, name, pictureUrl, hostedDomain));
		} catch (Exception ex) {
			log.warn("Google ID token verification rejected with exception: {}", ex.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/**
	 * Authoritative Google email verification policy:
	 * - email_verified must be true
	 * - domain is gmail.com / googlemail.com, OR hosted domain (hd) matches the email's domain
	 */
	public boolean isAuthoritativeGoogleEmail(GoogleUserPayload payload) {
		if (payload == null || !payload.emailVerified() || payload.email() == null) {
			return false;
		}
		int at = payload.email().lastIndexOf('@');
		if (at < 0 || at == payload.email().length() - 1) {
			return false;
		}
		String emailDomain = payload.email().substring(at + 1).toLowerCase(Locale.ROOT);
		if (CONSUMER_GOOGLE_DOMAINS.contains(emailDomain)) {
			return true;
		}
		String hd = payload.hostedDomain();
		return hd != null && !hd.isBlank() && hd.equalsIgnoreCase(emailDomain);
	}
}
