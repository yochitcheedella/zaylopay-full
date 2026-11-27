package com.zaylope.registration.applicant;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankEncryptionServiceTest {

	private BankEncryptionService encryptionService;

	@Test
	void shouldRejectMissingOrBlankExternalKey() {
		assertThrows(IllegalArgumentException.class, () -> new BankEncryptionService(null));
		assertThrows(IllegalArgumentException.class, () -> new BankEncryptionService("   "));
	}

	@BeforeEach
	void setUp() {
		encryptionService = new BankEncryptionService("test-secret-key-32-bytes-long!!");
	}

	@Test
	void shouldEncryptAndDecryptSuccessfully() {
		String original = "1234567890123456";
		String ciphertext = encryptionService.encrypt(original);

		assertNotNull(ciphertext);
		assertNotEquals(original, ciphertext);

		String decrypted = encryptionService.decrypt(ciphertext);
		assertEquals(original, decrypted);
	}

	@Test
	void shouldGenerateDifferentCiphertextsForSameInputDueToRandomIV() {
		String original = "987654321098";
		String c1 = encryptionService.encrypt(original);
		String c2 = encryptionService.encrypt(original);

		assertNotEquals(c1, c2, "Each encryption must use a unique random IV");
		assertEquals(original, encryptionService.decrypt(c1));
		assertEquals(original, encryptionService.decrypt(c2));
	}

	@Test
	void shouldMaskAccountNumberCorrectly() {
		assertEquals("•••• •••• •••• 3456", BankEncryptionService.maskAccountNumber("1234567890123456"));
		assertEquals("•••• •••• •••• 7890", BankEncryptionService.maskAccountNumber("1234567890"));
		assertEquals("•••• 1234", BankEncryptionService.maskAccountNumber("1234"));
		assertEquals("3456", BankEncryptionService.extractLast4("1234567890123456"));
	}

	@Test
	void shouldThrowExceptionOnTamperedCiphertext() {
		String original = "5555666677778888";
		String ciphertext = encryptionService.encrypt(original);

		// Tamper with ciphertext body (avoiding trailing Base64 padding bits)
		char[] chars = ciphertext.toCharArray();
		int idx = chars.length / 2;
		chars[idx] = (chars[idx] == 'A') ? 'B' : 'A';
		String tampered = new String(chars);

		assertThrows(Exception.class, () -> encryptionService.decrypt(tampered));
	}
}
