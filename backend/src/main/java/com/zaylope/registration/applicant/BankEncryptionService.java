package com.zaylope.registration.applicant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Provides AES-256-GCM authenticated encryption for sensitive bank account numbers.
 * Plaintext account numbers are never stored in MySQL.
 */
@Service
public class BankEncryptionService {

	private static final String ALGORITHM = "AES";
	private static final String TRANSFORMATION = "AES/GCM/NoPadding";
	private static final int GCM_IV_LENGTH_BYTES = 12;
	private static final int GCM_TAG_LENGTH_BITS = 128;

	private final SecretKey secretKey;
	private final SecureRandom secureRandom = new SecureRandom();

	public BankEncryptionService(
			@Value("${app.bank.encryption-key}") String keyString) {
		if (keyString == null || keyString.isBlank()) {
			throw new IllegalArgumentException("BANK_ENCRYPTION_KEY must be supplied externally");
		}
		try {
			// Ensure deterministic 256-bit key from configuration key using SHA-256
			byte[] keyBytes = MessageDigest.getInstance("SHA-256")
					.digest(keyString.getBytes(StandardCharsets.UTF_8));
			this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to initialize bank encryption service", e);
		}
	}

	public String encrypt(String plaintext) {
		if (plaintext == null || plaintext.isBlank()) {
			throw new IllegalArgumentException("Account number to encrypt cannot be empty");
		}
		try {
			byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

			byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			// Combined payload: [12-byte IV][ciphertext + 16-byte GCM authentication tag]
			byte[] combined = new byte[iv.length + cipherBytes.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

			return Base64.getEncoder().encodeToString(combined);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to encrypt bank account number", e);
		}
	}

	public String decrypt(String ciphertextBase64) {
		if (ciphertextBase64 == null || ciphertextBase64.isBlank()) {
			throw new IllegalArgumentException("Ciphertext to decrypt cannot be empty");
		}
		try {
			byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
			if (combined.length <= GCM_IV_LENGTH_BYTES) {
				throw new IllegalArgumentException("Invalid ciphertext format: payload too short");
			}

			byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
			System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);

			int cipherLen = combined.length - GCM_IV_LENGTH_BYTES;
			byte[] cipherBytes = new byte[cipherLen];
			System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherLen);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

			byte[] plaintext = cipher.doFinal(cipherBytes);
			return new String(plaintext, StandardCharsets.UTF_8);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to decrypt bank account number", e);
		}
	}

	public static String maskAccountNumber(String acc) {
		if (acc == null || acc.isBlank()) {
			return "•••• •••• •••• ••••";
		}
		String trimmed = acc.trim();
		if (trimmed.length() <= 4) {
			return "•••• " + trimmed;
		}
		String last4 = trimmed.substring(trimmed.length() - 4);
		return "•••• •••• •••• " + last4;
	}

	public static String extractLast4(String acc) {
		if (acc == null || acc.isBlank()) {
			return "";
		}
		String trimmed = acc.trim();
		return trimmed.length() <= 4 ? trimmed : trimmed.substring(trimmed.length() - 4);
	}
}
