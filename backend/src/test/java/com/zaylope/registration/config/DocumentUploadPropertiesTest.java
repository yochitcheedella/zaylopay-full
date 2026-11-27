package com.zaylope.registration.config;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentUploadPropertiesTest {

	@Test
	void acceptsAnExplicitDevelopmentPolicyWithPrivateAbsoluteStorage() {
		DocumentUploadProperties properties = properties(
				true,
				Path.of(System.getProperty("java.io.tmpdir"), "zaylope-private-documents").toString(),
				"Local development notice.",
				"Applicants may remove before submission.");

		assertTrue(properties.isPolicyConfigured());
	}

	@Test
	void rejectsMissingPrivacyNoticeOrDeletionPolicy() {
		String storage = Path.of(System.getProperty("java.io.tmpdir"), "zaylope-private-documents").toString();

		assertFalse(properties(true, storage, "", "Applicants may remove before submission.")
				.isPolicyConfigured());
		assertFalse(properties(true, storage, "Local development notice.", "")
				.isPolicyConfigured());
	}

	@Test
	void rejectsRelativeStorageDirectories() {
		assertFalse(properties(
				true,
				"private-documents",
				"Local development notice.",
				"Applicants may remove before submission.").isPolicyConfigured());
	}

	private static DocumentUploadProperties properties(
			boolean enabled,
			String storageDirectory,
			String privacyNotice,
			String deletionPolicy) {
		return new DocumentUploadProperties(
				enabled,
				storageDirectory,
				List.of("Identity proof"),
				List.of("Address proof"),
				"Test applicant document uploads.",
				privacyNotice,
				"The applicant session can access its own documents.",
				"Delete 30 days after upload.",
				"P30D",
				deletionPolicy,
				"local-development-v1",
				"0 31 2 * * *");
	}
}
