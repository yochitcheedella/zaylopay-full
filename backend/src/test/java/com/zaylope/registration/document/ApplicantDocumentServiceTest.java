package com.zaylope.registration.document;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantDocumentNotFoundException;
import com.zaylope.registration.common.exception.DocumentConsentRequiredException;
import com.zaylope.registration.common.exception.OnboardingConsentRequiredException;
import com.zaylope.registration.config.DocumentUploadProperties;
import com.zaylope.registration.registration.ApplicantRegistrationResponse;
import com.zaylope.registration.registration.ApplicantRegistrationStateResponse;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicantDocumentServiceTest {

	private ApplicantRepository applicantRepository;
	private ApplicantDocumentRepository documentRepository;
	private ApplicantDocumentAuditRepository auditRepository;
	private ApplicantDocumentFileValidator fileValidator;
	private PrivateApplicantDocumentStorage storage;
	private ApplicantDocumentService service;
	private DocumentUploadProperties properties;
	private Applicant applicant;

	@BeforeEach
	void setUp() {
		applicantRepository = mock(ApplicantRepository.class);
		documentRepository = mock(ApplicantDocumentRepository.class);
		auditRepository = mock(ApplicantDocumentAuditRepository.class);
		fileValidator = mock(ApplicantDocumentFileValidator.class);
		storage = mock(PrivateApplicantDocumentStorage.class);
		properties = enabledPolicy();
		applicant = mock(Applicant.class);
		service = new ApplicantDocumentService(
				applicantRepository,
				documentRepository,
				auditRepository,
				fileValidator,
				storage,
				properties);
	}

	@Test
	void refusesUploadWithoutConsentBeforeReadingOrSavingTheFile() {
		when(applicantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(applicant));
		when(applicant.isContactVerified()).thenReturn(true);
		when(applicant.getRegistrationSubmittedAt()).thenReturn(null);

		assertThrows(
				DocumentConsentRequiredException.class,
				() -> service.uploadOrReplace(5L, "Identity proof", new MockMultipartFile(
						"file", "identity.png", "image/png", new byte[] {1}), false));

		verify(fileValidator, never()).validate(org.mockito.ArgumentMatchers.any());
		verify(storage, never()).store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void servesConfiguredDocumentPolicyAndUploadRequirements() {
		when(applicantRepository.findById(5L)).thenReturn(Optional.of(applicant));
		when(applicant.isContactVerified()).thenReturn(true);

		ApplicantDocumentPolicyResponse response = service.policy(5L);

		assertEquals("Local development only. Use synthetic sample files.", response.privacyNotice());
		assertEquals("Test applicant document uploads.", response.collectionPurpose());
		assertEquals("Delete after 30 days.", response.retentionPolicy());
		assertEquals("Applicants may remove documents before submission.", response.deletionPolicy());
		assertEquals(List.of("PDF", "JPG/JPEG", "PNG"), response.acceptedFileFormats());
		assertEquals(10 * 1024 * 1024, response.maximumFileSizeBytes());
		assertEquals(List.of(
				new DocumentUploadProperties.DocumentTypeOption("Identity proof", true)),
				response.documentTypes());
	}

	@Test
	void acceptsDocumentUploadOnlyAfterBothVerificationChecks() {
		when(fileValidator.validate(org.mockito.ArgumentMatchers.any()))
				.thenReturn(new ApplicantDocumentFileValidator.ValidatedDocument(
						new byte[] {1}, "image/png", "png", "identity.png"));
		when(applicantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(applicant));
		when(applicant.isContactVerified()).thenReturn(true);
		when(applicant.getRegistrationSubmittedAt()).thenReturn(null);
		when(storage.store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
				.thenReturn("private-storage-id");

		org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
		try {
			ApplicantDocumentResponse response = service.uploadOrReplace(
					5L,
					"Identity proof",
					new MockMultipartFile("file", "identity.png", "image/png", new byte[] {1}),
					true);

			assertEquals("Identity proof", response.documentType());
			verify(documentRepository).saveAndFlush(org.mockito.ArgumentMatchers.any(ApplicantDocument.class));
		} finally {
			org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
		}
	}

	@Test
	void refusesDocumentReadsThatDoNotBelongToTheApplicant() {
		when(applicantRepository.findById(5L)).thenReturn(Optional.of(applicant));
		when(applicant.isContactVerified()).thenReturn(true);
		when(documentRepository.findByDocumentIdAndApplicantApplicantId("other-document", 5L))
				.thenReturn(Optional.empty());

		assertThrows(
				ApplicantDocumentNotFoundException.class,
				() -> service.applicantContent(5L, "other-document"));

		verify(storage, never()).read(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void blocksSubmissionWithoutConsent() {
		when(applicantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(applicant));
		when(applicant.getRegistrationSubmittedAt()).thenReturn(null);
		when(applicant.isContactVerified()).thenReturn(true);
		when(applicant.hasValidPersonalDetails()).thenReturn(true);
		when(applicant.hasPrivacyConsent()).thenReturn(false);

		assertThrows(OnboardingConsentRequiredException.class, () -> service.submit(5L, false, null));

		verify(applicant, never()).markRegistrationSubmitted(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void submitsVerifiedApplicantWithConsentAndWithoutDocuments() {
		when(applicantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(applicant));
		when(applicant.getRegistrationSubmittedAt()).thenReturn(null);
		when(applicant.isContactVerified()).thenReturn(true);
		when(applicant.hasValidPersonalDetails()).thenReturn(true);
		when(applicant.hasPrivacyConsent()).thenReturn(false);

		ApplicantRegistrationResponse response = service.submit(5L, true, "onboarding-v1");

		assertEquals("SUBMITTED", response.status());
		verify(applicant).recordPrivacyConsent(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("onboarding-v1"));
		verify(applicant).markRegistrationSubmitted(org.mockito.ArgumentMatchers.any());
		verify(applicantRepository).saveAndFlush(applicant);
	}

	@Test
	void blocksEveryOperationUntilAtLeastOneContactVerificationPasses() {
		when(applicantRepository.findById(5L)).thenReturn(Optional.of(applicant));
		when(applicantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(applicant));
		when(applicant.isContactVerified()).thenReturn(false);

		assertThrows(ApplicantVerificationRequiredException.class, () -> service.policy(5L));
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.list(5L));
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.uploadOrReplace(
				5L,
				"Identity proof",
				new MockMultipartFile("file", "identity.png", "image/png", new byte[] {1}),
				true));
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.remove(5L, "document-1"));
		assertThrows(
				ApplicantVerificationRequiredException.class,
				() -> service.applicantContent(5L, "document-1"));
		assertThrows(ApplicantVerificationRequiredException.class, () -> service.submit(5L, true, "onboarding-v1"));

		verify(documentRepository, never()).findAllByApplicantApplicantIdOrderByUploadedAtAsc(5L);
		verify(fileValidator, never()).validate(org.mockito.ArgumentMatchers.any());
		verify(storage, never()).store(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verify(storage, never()).read(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void registrationStateReflectsIndependentContactVerificationWithoutMarkingUnverifiedChannel() {
		Applicant realApplicant = new Applicant("Asha Devi", "+919876543210", "asha@example.com");
		realApplicant.markEmailVerified(java.time.Instant.now());
		when(applicantRepository.findById(5L)).thenReturn(Optional.of(realApplicant));
		when(documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(5L)).thenReturn(List.of());

		ApplicantRegistrationStateResponse state = service.registrationState(5L);

		org.junit.jupiter.api.Assertions.assertFalse(state.mobileVerified());
		org.junit.jupiter.api.Assertions.assertTrue(state.emailVerified());
		org.junit.jupiter.api.Assertions.assertTrue(state.contactVerified());
		org.junit.jupiter.api.Assertions.assertFalse(state.consentAccepted());
	}

	@Test
	void supportsAdministrativeReviewWithAuditTrail() {
		when(applicantRepository.findById(5L)).thenReturn(Optional.of(applicant));
		when(applicant.isContactVerified()).thenReturn(true);

		ApplicantDocument document = new ApplicantDocument(
				"doc-123",
				applicant,
				"Identity proof",
				"storage-id.png",
				"identity.png",
				"image/png",
				1024,
				java.time.Instant.now(),
				java.time.Instant.now(),
				"v1");
		when(documentRepository.findByDocumentIdAndApplicantApplicantId("doc-123", 5L))
				.thenReturn(Optional.of(document));

		ApplicantDocumentResponse approved = service.reviewDocument(
				5L, "doc-123", ApplicantDocumentVerificationStatus.APPROVED, "COMPLIANCE_OFFICER", null);
		assertEquals("APPROVED", approved.verificationStatus());

		ApplicantDocumentResponse rejected = service.reviewDocument(
				5L, "doc-123", ApplicantDocumentVerificationStatus.REJECTED, "COMPLIANCE_OFFICER", "BLURRY_IMAGE");
		assertEquals("REJECTED", rejected.verificationStatus());

		ApplicantDocumentResponse reupload = service.reviewDocument(
				5L, "doc-123", ApplicantDocumentVerificationStatus.RE_UPLOAD, "COMPLIANCE_OFFICER", "MASK_REQUIRED");
		assertEquals("RE_UPLOAD", reupload.verificationStatus());

		org.mockito.ArgumentCaptor<ApplicantDocumentAuditEvent> captor =
				org.mockito.ArgumentCaptor.forClass(ApplicantDocumentAuditEvent.class);
		verify(auditRepository, org.mockito.Mockito.times(3)).save(captor.capture());
		List<ApplicantDocumentAuditEvent> events = captor.getAllValues();
		assertEquals("COMPLIANCE_OFFICER", events.get(0).getActorRole());
		assertEquals("REVIEW_APPROVED", events.get(0).getAction());
		assertEquals("REVIEW_REJECTED:BLURRY_IMAGE", events.get(1).getAction());
		assertEquals("REVIEW_RE_UPLOAD:MASK_REQUIRED", events.get(2).getAction());
	}

	private static DocumentUploadProperties enabledPolicy() {
		return new DocumentUploadProperties(
				true,
				Path.of(System.getProperty("java.io.tmpdir"), "zaylope-document-test").toString(),
				List.of("Identity proof"),
				List.of(),
				"Test applicant document uploads.",
				"Local development only. Use synthetic sample files.",
				"Applicant and authorized reviewers.",
				"Delete after 30 days.",
				"P30D",
				"Applicants may remove documents before submission.",
				"identity-documents-v1",
				"0 31 2 * * *");
	}
}
