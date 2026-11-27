package com.zaylope.registration.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GoogleTokenVerifierServiceTest {

	private static final String CLIENT_ID = "454795409862-1t33iseetd4o3970r5rfjrbklkq2hc8f.apps.googleusercontent.com";

	@Test
	void verifyToken_whenMockTokenInRuntime_isAlwaysRejected() {
		GoogleTokenVerifierService service = new GoogleTokenVerifierService(CLIENT_ID);
		assertThat(service.verifyToken("mock-google-token:sub-123:user@gmail.com:Test User")).isEmpty();
	}

	@Test
	void verifyToken_whenClientIdMissing_disablesGoogleAuthAndRejectsTokens() {
		GoogleTokenVerifierService service = new GoogleTokenVerifierService("");
		assertThat(service.isConfigured()).isFalse();
		assertThat(service.verifyToken("header.payload.sig")).isEmpty();
	}

	@Test
	void verifyToken_whenNullOrBlankOrMalformed_returnsEmpty() {
		GoogleTokenVerifierService service = new GoogleTokenVerifierService(CLIENT_ID);
		assertThat(service.verifyToken(null)).isEmpty();
		assertThat(service.verifyToken("")).isEmpty();
		assertThat(service.verifyToken("   ")).isEmpty();
		assertThat(service.verifyToken("malformed.jwt.token")).isEmpty();
	}

	@Test
	void verifyToken_validatesAudienceIssuerAndClaims() throws Exception {
		GoogleIdTokenVerifier mockVerifier = Mockito.mock(GoogleIdTokenVerifier.class);
		GoogleTokenVerifierService service = new GoogleTokenVerifierService(CLIENT_ID, mockVerifier);

		GoogleIdToken validToken = mockGoogleToken("sub-123", "user@gmail.com", true, "https://accounts.google.com", CLIENT_ID, null);
		when(mockVerifier.verify("valid.jwt")).thenReturn(validToken);

		Optional<GoogleTokenVerifierService.GoogleUserPayload> result = service.verifyToken("valid.jwt");
		assertThat(result).isPresent();
		assertThat(result.get().sub()).isEqualTo("sub-123");
		assertThat(result.get().email()).isEqualTo("user@gmail.com");
		assertThat(service.isAuthoritativeGoogleEmail(result.get())).isTrue();

		// Wrong audience rejected
		GoogleIdToken wrongAud = mockGoogleToken("sub-123", "user@gmail.com", true, "https://accounts.google.com", "other-client", null);
		when(mockVerifier.verify("wrong-aud.jwt")).thenReturn(wrongAud);
		assertThat(service.verifyToken("wrong-aud.jwt")).isEmpty();

		// Untrusted issuer rejected
		GoogleIdToken wrongIss = mockGoogleToken("sub-123", "user@gmail.com", true, "https://evil.example", CLIENT_ID, null);
		when(mockVerifier.verify("wrong-iss.jwt")).thenReturn(wrongIss);
		assertThat(service.verifyToken("wrong-iss.jwt")).isEmpty();

		// Missing sub rejected
		GoogleIdToken missingSub = mockGoogleToken(" ", "user@gmail.com", true, "https://accounts.google.com", CLIENT_ID, null);
		when(mockVerifier.verify("no-sub.jwt")).thenReturn(missingSub);
		assertThat(service.verifyToken("no-sub.jwt")).isEmpty();
	}

	@Test
	void verifyTokenPreservesLocalPartCaseAndNormalizesDomain() throws Exception {
		GoogleIdTokenVerifier mockVerifier = Mockito.mock(GoogleIdTokenVerifier.class);
		GoogleTokenVerifierService service = new GoogleTokenVerifierService(CLIENT_ID, mockVerifier);
		GoogleIdToken token = mockGoogleToken(
				"sub-123", "User.Tag+Work@GMAIL.COM", true, "https://accounts.google.com", CLIENT_ID, null);
		when(mockVerifier.verify("mixed-case.jwt")).thenReturn(token);

		Optional<GoogleTokenVerifierService.GoogleUserPayload> result = service.verifyToken("mixed-case.jwt");

		assertThat(result).isPresent();
		assertThat(result.get().email()).isEqualTo("User.Tag+Work@gmail.com");
	}

	@Test
	void authoritativeGoogleEmailPolicyChecksDomainHdAndEmailVerified() {
		GoogleTokenVerifierService service = new GoogleTokenVerifierService(CLIENT_ID);

		assertThat(service.isAuthoritativeGoogleEmail(
				new GoogleTokenVerifierService.GoogleUserPayload("s1", "a@gmail.com", true, "A", null, null))).isTrue();
		assertThat(service.isAuthoritativeGoogleEmail(
				new GoogleTokenVerifierService.GoogleUserPayload("s2", "a@googlemail.com", true, "A", null, null))).isTrue();
		assertThat(service.isAuthoritativeGoogleEmail(
				new GoogleTokenVerifierService.GoogleUserPayload("s3", "a@acme.com", true, "A", null, "acme.com"))).isTrue();

		// Third-party domain without Workspace hd -> not authoritative
		assertThat(service.isAuthoritativeGoogleEmail(
				new GoogleTokenVerifierService.GoogleUserPayload("s4", "a@yahoo.com", true, "A", null, null))).isFalse();
		// Workspace hd mismatch -> not authoritative
		assertThat(service.isAuthoritativeGoogleEmail(
				new GoogleTokenVerifierService.GoogleUserPayload("s5", "a@acme.com", true, "A", null, "other.com"))).isFalse();
		// email_verified=false -> never authoritative
		assertThat(service.isAuthoritativeGoogleEmail(
				new GoogleTokenVerifierService.GoogleUserPayload("s6", "a@gmail.com", false, "A", null, null))).isFalse();
	}

	private static GoogleIdToken mockGoogleToken(
			String sub,
			String email,
			boolean emailVerified,
			String issuer,
			String audience,
			String hd) {
		GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
		payload.setSubject(sub);
		payload.setEmail(email);
		payload.setEmailVerified(emailVerified);
		payload.setIssuer(issuer);
		payload.setAudience(List.of(audience));
		if (hd != null) {
			payload.setHostedDomain(hd);
		}
		payload.set("name", "Test User");
		return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
	}
}
