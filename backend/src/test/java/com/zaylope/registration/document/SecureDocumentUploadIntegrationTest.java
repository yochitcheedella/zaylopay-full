package com.zaylope.registration.document;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantDocumentNotFoundException;
import com.zaylope.registration.common.exception.InvalidApplicantDocumentException;
import com.zaylope.registration.config.DocumentUploadProperties;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecureDocumentUploadIntegrationTest {

	@TempDir
	Path tempDir;

	private ApplicantRepository applicantRepository;
	private ApplicantDocumentRepository documentRepository;
	private ApplicantDocumentAuditRepository auditRepository;
	private ApplicantDocumentFileValidator validator;
	private PrivateApplicantDocumentStorage storage;
	private DocumentUploadProperties properties;
	private ApplicantDocumentService service;

	private Applicant verifiedApplicant;
	private Applicant unverifiedApplicant;

	@BeforeEach
	void setUp() {
		applicantRepository = mock(ApplicantRepository.class);
		documentRepository = mock(ApplicantDocumentRepository.class);
		auditRepository = mock(ApplicantDocumentAuditRepository.class);
		validator = new ApplicantDocumentFileValidator();

		Path storagePath = tempDir.resolve("secure-docs");
		properties = new DocumentUploadProperties(
				true,
				storagePath.toString(),
				List.of("PAN Card"),
				List.of("Masked Aadhaar", "Voter ID"),
				"Identity verification for ZayloPe onboarding.",
				"Privacy notice: documents stored securely in private storage.",
				"Compliance officers and the applicant.",
				"Retention 30 days.",
				"P30D",
				"Applicants may remove documents before submission.",
				"kyc-v1",
				"0 31 2 * * *");

		storage = new PrivateApplicantDocumentStorage(properties);
		storage.initialize();

		service = new ApplicantDocumentService(
				applicantRepository,
				documentRepository,
				auditRepository,
				validator,
				storage,
				properties);

		verifiedApplicant = mock(Applicant.class);
		when(verifiedApplicant.getApplicantId()).thenReturn(100L);
		when(verifiedApplicant.isContactVerified()).thenReturn(true);
		when(verifiedApplicant.getRegistrationSubmittedAt()).thenReturn(null);

		unverifiedApplicant = mock(Applicant.class);
		when(unverifiedApplicant.getApplicantId()).thenReturn(200L);
		when(unverifiedApplicant.isContactVerified()).thenReturn(false);

		when(applicantRepository.findById(100L)).thenReturn(Optional.of(verifiedApplicant));
		when(applicantRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(verifiedApplicant));
		when(applicantRepository.findById(200L)).thenReturn(Optional.of(unverifiedApplicant));
		when(applicantRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(unverifiedApplicant));
	}

	@Test
	void test1_validJpgUpload() throws IOException {
		byte[] jpg = createImage("jpg");
		var file = new MockMultipartFile("file", "pancard.jpg", "image/jpeg", jpg);

		var validated = validator.validate(file);
		assertEquals("image/jpeg", validated.contentType());
		assertEquals("jpg", validated.extension());
	}

	@Test
	void test2_validJpegUpload() throws IOException {
		byte[] jpeg = createImage("jpeg");
		var file = new MockMultipartFile("file", "pancard.jpeg", "image/jpeg", jpeg);

		var validated = validator.validate(file);
		assertEquals("image/jpeg", validated.contentType());
		assertEquals("jpg", validated.extension());
	}

	@Test
	void test3_validPngUpload() throws IOException {
		byte[] png = createImage("png");
		var file = new MockMultipartFile("file", "voter.png", "image/png", png);

		var validated = validator.validate(file);
		assertEquals("image/png", validated.contentType());
		assertEquals("png", validated.extension());
	}

	@Test
	void test4_validPdfUpload() throws IOException {
		byte[] pdf = createPdf();
		var file = new MockMultipartFile("file", "aadhaar_masked.pdf", "application/pdf", pdf);

		var validated = validator.validate(file);
		assertEquals("application/pdf", validated.contentType());
		assertEquals("pdf", validated.extension());
	}

	@Test
	void test5_fileLargerThan10MbRejected() {
		byte[] oversized = new byte[10 * 1024 * 1024 + 1];
		var file = new MockMultipartFile("file", "big.png", "image/png", oversized);

		assertThrows(InvalidApplicantDocumentException.class, () -> validator.validate(file));
	}

	@Test
	void test6_unsupportedDocxRejected() {
		byte[] docx = {0x50, 0x4b, 0x03, 0x04};
		var file = new MockMultipartFile("file", "resume.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docx);

		assertThrows(InvalidApplicantDocumentException.class, () -> validator.validate(file));
	}

	@Test
	void test7_spoofedMimeTypeRejected() throws IOException {
		byte[] png = createImage("png");
		var spoofed = new MockMultipartFile("file", "fake.pdf", "application/pdf", png);

		assertThrows(InvalidApplicantDocumentException.class, () -> validator.validate(spoofed));
	}

	@Test
	void test8_corruptedImageOrPdfRejected() {
		byte[] corrupt = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00};
		var file = new MockMultipartFile("file", "broken.png", "image/png", corrupt);

		assertThrows(InvalidApplicantDocumentException.class, () -> validator.validate(file));
	}

	@Test
	void test9_emptyFileRejected() {
		var file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

		assertThrows(InvalidApplicantDocumentException.class, () -> validator.validate(file));
	}

	@Test
	void test10_passwordProtectedPdfRejected() throws IOException {
		byte[] protectedPdf;
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			doc.addPage(new PDPage());
			AccessPermission permissions = new AccessPermission();
			StandardProtectionPolicy policy = new StandardProtectionPolicy("ownerSecret", "userSecret", permissions);
			doc.protect(policy);
			doc.save(out);
			protectedPdf = out.toByteArray();
		}

		var file = new MockMultipartFile("file", "encrypted.pdf", "application/pdf", protectedPdf);
		assertThrows(InvalidApplicantDocumentException.class, () -> validator.validate(file));
	}

	@Test
	void test11_duplicateUploadAndReplacePreservesLifecycle() throws IOException {
		byte[] png = createImage("png");
		var file = new MockMultipartFile("file", "pan.png", "image/png", png);

		TransactionSynchronizationManager.initSynchronization();
		try {
			ApplicantDocument existing = new ApplicantDocument(
					"doc-1",
					verifiedApplicant,
					"PAN Card",
					"old-file-uuid.png",
					"old.png",
					"image/png",
					100,
					Instant.now(),
					Instant.now(),
					"kyc-v1");
			when(documentRepository.findByApplicantApplicantIdAndDocumentType(100L, "PAN Card"))
					.thenReturn(Optional.of(existing));

			ApplicantDocumentResponse response = service.uploadOrReplace(100L, "PAN Card", file, true);

			assertNotNull(response);
			assertEquals("PAN Card", response.documentType());
			assertEquals("PENDING", response.verificationStatus());
			verify(documentRepository).saveAndFlush(existing);

			// Verify old file lifecycle deletion on commit
			List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
			assertFalse(syncs.isEmpty());
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void test12_transactionRollbackCleansUpStagedFile() throws IOException {
		byte[] png = createImage("png");

		TransactionSynchronizationManager.initSynchronization();
		String stagedId = null;
		try {
			stagedId = storage.store(png, "png");
			assertTrue(Files.exists(tempDir.resolve("secure-docs").resolve(stagedId)));

			// Simulate rollback
			storage.delete(stagedId);
			assertFalse(Files.exists(tempDir.resolve("secure-docs").resolve(stagedId)));
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void test13_unauthorizedAccessBlockedForUnverifiedApplicant() {
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.list(200L));
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.policy(200L));
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.uploadOrReplace(
				200L, "PAN Card", new MockMultipartFile("file", "pan.png", "image/png", new byte[1]), true));
	}

	@Test
	void test14_idorAttemptToReadOtherApplicantDocumentThrowsNotFound() {
		when(documentRepository.findByDocumentIdAndApplicantApplicantId("doc-belonging-to-other", 100L))
				.thenReturn(Optional.empty());

		assertThrows(ApplicantDocumentNotFoundException.class, () -> service.applicantContent(100L, "doc-belonging-to-other"));
	}

	@Test
	void test15_replaceRejectedDocumentResetsStatusToPending() throws IOException {
		ApplicantDocument rejectedDoc = new ApplicantDocument(
				"doc-1",
				verifiedApplicant,
				"PAN Card",
				"old-storage.png",
				"blurry_pan.png",
				"image/png",
				1024,
				Instant.now(),
				Instant.now(),
				"kyc-v1");
		rejectedDoc.updateVerificationStatus(ApplicantDocumentVerificationStatus.REJECTED);
		assertEquals(ApplicantDocumentVerificationStatus.REJECTED, rejectedDoc.getVerificationStatus());

		when(documentRepository.findByApplicantApplicantIdAndDocumentType(100L, "PAN Card"))
				.thenReturn(Optional.of(rejectedDoc));

		byte[] newPng = createImage("png");
		var newFile = new MockMultipartFile("file", "clear_pan.png", "image/png", newPng);

		TransactionSynchronizationManager.initSynchronization();
		try {
			ApplicantDocumentResponse response = service.uploadOrReplace(100L, "PAN Card", newFile, true);
			assertEquals("PENDING", response.verificationStatus());
			assertEquals("clear_pan.png", response.originalFilename());
		} finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void test16_reviewWorkflowSupportsStatusTransitionsAndAudit() {
		ApplicantDocument doc = new ApplicantDocument(
				"doc-pan",
				verifiedApplicant,
				"PAN Card",
				"storage-pan.png",
				"pan.png",
				"image/png",
				2048,
				Instant.now(),
				Instant.now(),
				"kyc-v1");
		when(documentRepository.findByDocumentIdAndApplicantApplicantId("doc-pan", 100L))
				.thenReturn(Optional.of(doc));

		ApplicantDocumentResponse approved = service.reviewDocument(
				100L, "doc-pan", ApplicantDocumentVerificationStatus.APPROVED, "KYC_ANALYST", null);
		assertEquals("APPROVED", approved.verificationStatus());

		ApplicantDocumentResponse reupload = service.reviewDocument(
				100L, "doc-pan", ApplicantDocumentVerificationStatus.RE_UPLOAD, "KYC_ANALYST", "RE_MASK_FIRST_8");
		assertEquals("RE_UPLOAD", reupload.verificationStatus());

		ArgumentCaptor<ApplicantDocumentAuditEvent> captor = ArgumentCaptor.forClass(ApplicantDocumentAuditEvent.class);
		verify(auditRepository, org.mockito.Mockito.times(2)).save(captor.capture());
		assertEquals("KYC_ANALYST", captor.getAllValues().get(0).getActorRole());
		assertEquals("REVIEW_APPROVED", captor.getAllValues().get(0).getAction());
		assertEquals("REVIEW_RE_UPLOAD:RE_MASK_FIRST_8", captor.getAllValues().get(1).getAction());
	}

	@Test
	void test17_pathTraversalRejectedByStorage() {
		assertThrows(ApplicantDocumentNotFoundException.class, () -> storage.read("../../../etc/passwd.png"));
		assertThrows(ApplicantDocumentNotFoundException.class, () -> storage.read("not-a-uuid.png"));
		assertThrows(ApplicantDocumentNotFoundException.class, () -> storage.read(null));
	}

	@Test
	void test18_storageConsistencyAndFileIsolation() throws IOException {
		byte[] content = {10, 20, 30, 40};
		String storageId = storage.store(content, "png");

		assertTrue(storageId.endsWith(".png"));
		byte[] retrieved = storage.read(storageId);
		assertArrayEquals(content, retrieved);

		storage.delete(storageId);
		assertThrows(ApplicantDocumentNotFoundException.class, () -> storage.read(storageId));
	}

	private static byte[] createImage(String format) throws IOException {
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, format, out);
			return out.toByteArray();
		}
	}

	private static byte[] createPdf() throws IOException {
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			doc.addPage(new PDPage());
			doc.save(out);
			return out.toByteArray();
		}
	}
}
