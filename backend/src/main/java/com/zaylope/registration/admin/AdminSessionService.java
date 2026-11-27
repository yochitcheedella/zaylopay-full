package com.zaylope.registration.admin;

import com.zaylope.registration.common.exception.AdminUnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminSessionService {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminSessionService.class);
	private static final Duration SESSION_DURATION = Duration.ofHours(8);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final AdminUserRepository adminUserRepository;
	private final AdminSessionRepository adminSessionRepository;
	private final AdminPasswordEncoder passwordEncoder;
	private final AdminLoginRateLimiter rateLimiter;

	public AdminSessionService(
			AdminUserRepository adminUserRepository,
			AdminSessionRepository adminSessionRepository,
			AdminPasswordEncoder passwordEncoder,
			AdminLoginRateLimiter rateLimiter) {
		this.adminUserRepository = adminUserRepository;
		this.adminSessionRepository = adminSessionRepository;
		this.passwordEncoder = passwordEncoder;
		this.rateLimiter = rateLimiter;
	}

	@Transactional
	public String login(String username, String rawPassword) {
		return login(username, rawPassword, null);
	}

	@Transactional
	public String login(String username, String rawPassword, String clientIp) {
		String cleanUsername = username != null ? username.trim() : "";
		rateLimiter.checkAllowed(clientIp, cleanUsername);

		AdminUser admin = adminUserRepository.findByUsernameIgnoreCase(cleanUsername)
				.orElse(null);

		if (admin == null) {
			rateLimiter.recordFailure(clientIp, cleanUsername);
			LOGGER.warn("Admin login failed: unknown username '{}' from IP '{}'", cleanUsername, clientIp);
			throw new AdminUnauthorizedException("Invalid admin username or password.");
		}

		if (!admin.isActive()) {
			rateLimiter.recordFailure(clientIp, cleanUsername);
			LOGGER.warn("Admin login blocked: account '{}' is disabled from IP '{}'", cleanUsername, clientIp);
			throw new AdminUnauthorizedException("Admin account is disabled.");
		}

		if (!passwordEncoder.matches(rawPassword, admin.getPasswordHash())) {
			rateLimiter.recordFailure(clientIp, cleanUsername);
			LOGGER.warn("Admin login failed: invalid password for '{}' from IP '{}'", cleanUsername, clientIp);
			throw new AdminUnauthorizedException("Invalid admin username or password.");
		}

		// Reset failure counter upon successful authentication
		rateLimiter.recordSuccess(clientIp, cleanUsername);

		// Seamless migration: upgrade legacy SHA-256 hashes to BCrypt
		if (passwordEncoder.isLegacySha256(admin.getPasswordHash())) {
			String upgradedHash = passwordEncoder.encode(rawPassword);
			admin.updatePasswordHash(upgradedHash);
			adminUserRepository.save(admin);
			LOGGER.info("Admin account '{}' password hash upgraded from legacy SHA-256 to BCrypt.", cleanUsername);
		}

		// Generate random 32-byte cryptographic session token
		byte[] tokenBytes = new byte[32];
		RANDOM.nextBytes(tokenBytes);
		String rawToken = HexFormat.of().formatHex(tokenBytes);

		String tokenHash = hashToken(rawToken);
		Instant expiresAt = Instant.now().plus(SESSION_DURATION);

		AdminSession session = new AdminSession(admin.getAdminId(), tokenHash, expiresAt);
		adminSessionRepository.save(session);

		LOGGER.info("Admin '{}' logged in successfully from IP '{}'. Session expires at {}", cleanUsername, clientIp, expiresAt);
		return rawToken;
	}

	@Transactional(readOnly = true)
	public Optional<AdminUser> findActiveAdmin(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}

		String tokenHash = hashToken(rawToken.trim());
		return adminSessionRepository
				.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(tokenHash, Instant.now())
				.flatMap(session -> adminUserRepository.findById(session.getAdminId()))
				.filter(AdminUser::isActive);
	}

	@Transactional
	public void logout(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}

		String tokenHash = hashToken(rawToken.trim());
		adminSessionRepository.findByTokenHash(tokenHash).ifPresent(AdminSession::revoke);
	}

	public static String hashPassword(String password) {
		return AdminPasswordEncoder.hashSha256(password);
	}

	public static String hashToken(String token) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}
}
