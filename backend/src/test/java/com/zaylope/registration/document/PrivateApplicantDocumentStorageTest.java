package com.zaylope.registration.document;

import com.zaylope.registration.common.exception.ApplicantDocumentNotFoundException;
import com.zaylope.registration.config.DocumentUploadProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateApplicantDocumentStorageTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void storesUnderPrivateRootWithGeneratedNameAndRejectsTraversal() throws Exception {
		Path storageDirectory = temporaryDirectory.resolve("private-documents");
		DocumentUploadProperties properties = new DocumentUploadProperties(
				true,
				storageDirectory.toString(),
				List.of("Identity proof"),
				List.of(),
				"Test applicant document uploads.",
				"Local development only. Use synthetic sample files.",
				"The applicant session can access only its own documents.",
				"Delete 30 days after upload.",
				"P30D",
				"Applicants may remove documents before submission.",
				"local-development-v1",
				"0 31 2 * * *");
		PrivateApplicantDocumentStorage storage = new PrivateApplicantDocumentStorage(properties);
		storage.initialize();

		byte[] contents = {1, 2, 3};
		String identifier = storage.store(contents, "png");
		Path storedFile = storageDirectory.resolve(identifier);

		assertTrue(identifier.matches("[0-9a-f-]{36}\\.png"));
		assertTrue(storedFile.startsWith(storageDirectory));
		assertTrue(Files.exists(storedFile));
		assertArrayEquals(contents, storage.read(identifier));
		assertThrows(ApplicantDocumentNotFoundException.class, () -> storage.read("../secret.txt"));

		storage.delete(identifier);
		assertFalse(Files.exists(storedFile));
	}
}
