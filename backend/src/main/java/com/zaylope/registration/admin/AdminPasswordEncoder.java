package com.zaylope.registration.admin;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Enterprise password encoder for ZayloPe administrators.
 * Uses BCrypt with configurable work factor (default 12).
 * Provides seamless backwards-compatible verification and migration for legacy SHA-256 hashes.
 */
@Component
public class AdminPasswordEncoder {

	private final BCryptPasswordEncoder bCryptPasswordEncoder;

	public AdminPasswordEncoder(
			@Value("${app.security.admin.bcrypt-strength:12}") int strength) {
		int safeStrength = Math.min(Math.max(strength, 10), 16);
		this.bCryptPasswordEncoder = new BCryptPasswordEncoder(safeStrength);
	}

	public String encode(String rawPassword) {
		if (rawPassword == null || rawPassword.isBlank()) {
			throw new IllegalArgumentException("Password cannot be blank.");
		}
		return bCryptPasswordEncoder.encode(rawPassword);
	}

	public boolean matches(String rawPassword, String storedHash) {
		if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
			return false;
		}

		// 1. Standard BCrypt hash verification ($2a$, $2b$, $2y$)
		if (isBcrypt(storedHash)) {
			return bCryptPasswordEncoder.matches(rawPassword, storedHash);
		}

		// 2. Backwards-compatible legacy SHA-256 verification (for migration)
		if (isLegacySha256(storedHash)) {
			String calculated = hashSha256(rawPassword);
			return MessageDigest.isEqual(
					storedHash.toLowerCase().getBytes(StandardCharsets.UTF_8),
					calculated.toLowerCase().getBytes(StandardCharsets.UTF_8));
		}

		return false;
	}

	public boolean isBcrypt(String hash) {
		return hash != null && (hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));
	}

	public boolean isLegacySha256(String hash) {
		return hash != null && hash.length() == 64 && !hash.startsWith("$");
	}

	public static String hashSha256(String text) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 algorithm unavailable", e);
		}
	}
}
