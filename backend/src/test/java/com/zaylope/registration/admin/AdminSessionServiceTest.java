package com.zaylope.registration.admin;

import com.zaylope.registration.common.exception.AdminLoginRateLimitException;
import com.zaylope.registration.common.exception.AdminUnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSessionServiceTest {

	private AdminUserRepository adminUserRepository;
	private AdminSessionRepository adminSessionRepository;
	private AdminPasswordEncoder passwordEncoder;
	private AdminLoginRateLimiter rateLimiter;
	private AdminSessionService service;

	@BeforeEach
	void setUp() {
		adminUserRepository = Mockito.mock(AdminUserRepository.class);
		adminSessionRepository = Mockito.mock(AdminSessionRepository.class);
		passwordEncoder = new AdminPasswordEncoder(10);
		rateLimiter = new AdminLoginRateLimiter(5, 15);
		service = new AdminSessionService(adminUserRepository, adminSessionRepository, passwordEncoder, rateLimiter);
	}

	@Test
	void shouldAuthenticateAdminWithValidBcryptCredentials() {
		String bcryptHash = passwordEncoder.encode("superSecretPass123");
		AdminUser admin = new AdminUser("admin", "admin@zaylope.com", bcryptHash, "Super Admin", "SUPER_ADMIN");

		when(adminUserRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));

		String token = service.login("admin", "superSecretPass123", "127.0.0.1");

		assertNotNull(token);
		assertEquals(64, token.length(), "Session token must be 32 bytes hex (64 chars)");
		verify(adminSessionRepository).save(any(AdminSession.class));
	}

	@Test
	void shouldAuthenticateAndUpgradeLegacySha256Credentials() {
		// Calculate raw SHA-256 hash (64 hex characters)
		String rawPassword = "legacyPassword456";
		String legacySha256;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			legacySha256 = HexFormat.of().formatHex(digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		AdminUser admin = new AdminUser("admin_legacy", "legacy@zaylope.com", legacySha256, "Legacy Admin", "ADMIN");
		when(adminUserRepository.findByUsernameIgnoreCase("admin_legacy")).thenReturn(Optional.of(admin));

		String token = service.login("admin_legacy", rawPassword, "127.0.0.1");

		assertNotNull(token);
		assertTrue(admin.getPasswordHash().startsWith("$2a$"), "Password hash should be upgraded to BCrypt");
		verify(adminUserRepository).save(admin);
		verify(adminSessionRepository).save(any(AdminSession.class));
	}

	@Test
	void shouldRejectAdminLoginWithWrongPassword() {
		String bcryptHash = passwordEncoder.encode("correctPassword");
		AdminUser admin = new AdminUser("admin", "admin@zaylope.com", bcryptHash, "Admin User", "ADMIN");

		when(adminUserRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));

		assertThrows(AdminUnauthorizedException.class, () -> service.login("admin", "wrong_password", "127.0.0.1"));
	}

	@Test
	void shouldRejectAdminLoginWhenUserNotFound() {
		when(adminUserRepository.findByUsernameIgnoreCase("unknown")).thenReturn(Optional.empty());

		assertThrows(AdminUnauthorizedException.class, () -> service.login("unknown", "any_password", "127.0.0.1"));
	}

	@Test
	void shouldBlockAdminLoginWhenAccountIsDisabled() {
		String bcryptHash = passwordEncoder.encode("correctPassword");
		AdminUser admin = new AdminUser("admin", "admin@zaylope.com", bcryptHash, "Admin User", "ADMIN");
		admin.setActive(false);

		when(adminUserRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(admin));

		assertThrows(AdminUnauthorizedException.class, () -> service.login("admin", "correctPassword", "127.0.0.1"));
	}

	@Test
	void shouldEnforceRateLimitingAfterFiveFailedAttempts() {
		String bcryptHash = passwordEncoder.encode("correctPassword");
		AdminUser admin = new AdminUser("target_admin", "target@zaylope.com", bcryptHash, "Target Admin", "ADMIN");
		when(adminUserRepository.findByUsernameIgnoreCase("target_admin")).thenReturn(Optional.of(admin));

		// 5 failed attempts
		for (int i = 0; i < 5; i++) {
			final String badPass = "bad_pass_" + i;
			assertThrows(AdminUnauthorizedException.class, () ->
					service.login("target_admin", badPass, "192.168.1.100"));
		}

		// 6th attempt should trigger AdminLoginRateLimitException
		AdminLoginRateLimitException rateEx = assertThrows(AdminLoginRateLimitException.class, () ->
				service.login("target_admin", "correctPassword", "192.168.1.100"));

		assertTrue(rateEx.getMessage().contains("Too many failed login attempts"));
		assertTrue(rateEx.getRetryAfterSeconds() > 0);
	}
}
