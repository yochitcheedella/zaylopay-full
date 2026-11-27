package com.zaylope.registration.document;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantDocumentNotFoundException;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.common.exception.DocumentConsentRequiredException;
import com.zaylope.registration.common.exception.DocumentUploadNotConfiguredException;
import com.zaylope.registration.common.exception.InvalidApplicantDocumentException;
import com.zaylope.registration.common.exception.OnboardingConsentRequiredException;
import com.zaylope.registration.common.exception.RegistrationAlreadySubmittedException;
import com.zaylope.registration.config.DocumentUploadProperties;
import com.zaylope.registration.registration.ApplicantRegistrationResponse;
import com.zaylope.registration.registration.ApplicantRegistrationStateResponse;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import com.zaylope.registration.verification.ApplicantContactMasker;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class ApplicantDocumentService {

	private static final String APPLICANT_ROLE = "APPLICANT";
	private static final String SYSTEM_ROLE = "SYSTEM";

	private final ApplicantRepository applicantRepository;
	private final ApplicantDocumentRepository documentRepository;
	private final ApplicantDocumentAuditRepository auditRepository;
	private final ApplicantDocumentFileValidator fileValidator;
	private final PrivateApplicantDocumentStorage storage;
	private final DocumentUploadProperties properties;

	public ApplicantDocumentService(
			ApplicantRepository applicantRepository,
			ApplicantDocumentRepository documentRepository,
			ApplicantDocumentAuditRepository auditRepository,
			ApplicantDocumentFileValidator fileValidator,
			PrivateApplicantDocumentStorage storage,
			DocumentUploadProperties properties) {
		this.applicantRepository = applicantRepository;
		this.documentRepository = documentRepository;
		this.auditRepository = auditRepository;
		this.fileValidator = fileValidator;
		this.storage = storage;
		this.properties = properties;
	}

	public ApplicantDocumentPolicyResponse policy(long applicantId) {
		requireVerifiedApplicant(requireApplicant(applicantId));
		requireConfiguredPolicy();
		return new ApplicantDocumentPolicyResponse(
				properties.privacyNotice(),
				properties.privacyNoticeVersion(),
				properties.collectionPurpose(),
				properties.authorizedPersonnel(),
				properties.retentionPolicy(),
				properties.retentionPeriod(),
				properties.deletionPolicy(),
				ApplicantDocumentFileValidator.ACCEPTED_FILE_FORMATS,
				DocumentUploadProperties.MAX_FILE_SIZE_BYTES,
				properties.documentTypes());
	}

	@Transactional(readOnly = true)
	public List<ApplicantDocumentResponse> list(long applicantId) {
		requireVerifiedApplicant(requireApplicant(applicantId));
		requireConfiguredPolicy();
		return documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(applicantId)
				.stream()
				.map(ApplicantDocumentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public ApplicantRegistrationStateResponse registrationState(long applicantId) {
		Applicant applicant = requireApplicant(applicantId);
		boolean verified = isContactVerified(applicant);
		List<ApplicantDocumentResponse> documents = verified
				? documentRepository.findAllByApplicantApplicantIdOrderByUploadedAtAsc(applicantId)
						.stream()
						.map(ApplicantDocumentResponse::from)
						.toList()
				: List.of();
		return new ApplicantRegistrationStateResponse(
				ApplicantContactMasker.maskMobile(applicant.getMobileNumber()),
				ApplicantContactMasker.maskEmail(applicant.getEmail()),
				applicant.isMobileVerified(),
				applicant.isTrustedEmailVerified(),
				applicant.getRegistrationStatus(),
				documents,
				applicant.getFullName(),
				"APP-" + applicant.getApplicantId(),
				verified,
				applicant.hasPrivacyConsent(),
				applicant.getEmailVerificationMethod(),
				applicant.getPaymentStatus(),
				applicant.getApprovalStatus());
	}

	@Transactional
	public ApplicantDocumentResponse uploadOrReplace(
			long applicantId,
			String requestedType,
			org.springframework.web.multipart.MultipartFile file,
			boolean consented) {
		Applicant applicant = findEditableApplicant(applicantId);
		requireConfiguredPolicy();
		if (!consented) {
			throw new DocumentConsentRequiredException();
		}
		String documentType = properties.configuredType(requestedType);
		if (documentType == null) {
			throw new InvalidApplicantDocumentException();
		}
		ApplicantDocumentFileValidator.ValidatedDocument validated = fileValidator.validate(file);

		ApplicantDocument existing = documentRepository
				.findByApplicantApplicantIdAndDocumentType(applicantId, documentType)
				.orElse(null);
		String newStorageIdentifier = storage.store(validated.content(), validated.extension());
		String oldStorageIdentifier = existing == null ? null : existing.getStorageIdentifier();
		registerFileLifecycle(newStorageIdentifier, oldStorageIdentifier);
		Instant now = Instant.now();

		ApplicantDocument document;
		String action;
		if (existing == null) {
			document = new ApplicantDocument(
					UUID.randomUUID().toString(),
					applicant,
					documentType,
					newStorageIdentifier,
					validated.originalFilename(),
					validated.contentType(),
					validated.content().length,
					now,
					now,
					properties.privacyNoticeVersion());
			action = "UPLOAD";
		} else {
			existing.replaceFile(
					newStorageIdentifier,
					validated.originalFilename(),
					validated.contentType(),
					validated.content().length,
					now,
					now,
					properties.privacyNoticeVersion());
			document = existing;
			action = "REPLACE";
		}

		documentRepository.saveAndFlush(document);
		recordAudit(document, applicantId, APPLICANT_ROLE, action, now);
		return ApplicantDocumentResponse.from(document);
	}

	@Transactional
	public void remove(long applicantId, String documentId) {
		findEditableApplicant(applicantId);
		requireConfiguredPolicy();
		ApplicantDocument document = documentRepository
				.findByDocumentIdAndApplicantApplicantId(documentId, applicantId)
				.orElseThrow(ApplicantDocumentNotFoundException::new);
		Instant now = Instant.now();
		recordAudit(document, applicantId, APPLICANT_ROLE, "REMOVE", now);
		String storageIdentifier = document.getStorageIdentifier();
		documentRepository.delete(document);
		registerFileLifecycle(null, storageIdentifier);
	}

	@Transactional
	public ApplicantDocumentContent applicantContent(long applicantId, String documentId) {
		requireVerifiedApplicant(requireApplicant(applicantId));
		requireConfiguredPolicy();
		ApplicantDocument document = documentRepository
				.findByDocumentIdAndApplicantApplicantId(documentId, applicantId)
				.orElseThrow(ApplicantDocumentNotFoundException::new);
		recordAudit(document, applicantId, APPLICANT_ROLE, "APPLICANT_READ", Instant.now());
		return content(document);
	}

	@Transactional
	public ApplicantDocumentContent adminContent(long applicantId, String documentId, String adminUsername) {
		requireApplicant(applicantId);
		requireConfiguredPolicy();
		ApplicantDocument document = documentRepository
				.findByDocumentIdAndApplicantApplicantId(documentId, applicantId)
				.orElseThrow(ApplicantDocumentNotFoundException::new);
		recordAudit(document, applicantId, "ADMIN", "ADMIN_READ:" + (adminUsername != null ? adminUsername : "admin"), Instant.now());
		return content(document);
	}

	@Transactional
	public ApplicantDocumentResponse reviewDocument(
			long applicantId,
			String documentId,
			ApplicantDocumentVerificationStatus newStatus,
			String reviewerRole,
			String rejectionReason) {
		requireApplicant(applicantId);
		requireConfiguredPolicy();
		if (newStatus == ApplicantDocumentVerificationStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
			throw new IllegalArgumentException("Rejection reason is required when rejecting a document.");
		}
		ApplicantDocument document = documentRepository
				.findByDocumentIdAndApplicantApplicantId(documentId, applicantId)
				.orElseThrow(ApplicantDocumentNotFoundException::new);
		document.updateVerificationStatus(newStatus);
		documentRepository.saveAndFlush(document);
		String action = "REVIEW_" + (newStatus == null ? "PENDING" : newStatus.name());
		if (rejectionReason != null && !rejectionReason.isBlank()) {
			String sanitized = rejectionReason.replaceAll("[^A-Za-z0-9_ -]", "").trim();
			action = action + ":" + sanitized;
		}
		if (action.length() > 32) {
			action = action.substring(0, 32);
		}
		recordAudit(document, applicantId, reviewerRole != null ? reviewerRole : "ADMIN", action, Instant.now());
		return ApplicantDocumentResponse.from(document);
	}

	@Transactional
	public ApplicantRegistrationStateResponse updateFullName(long applicantId, String requestedFullName) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);
		if (applicant.getRegistrationSubmittedAt() != null || applicant.isPaymentPaid()) {
			throw new RegistrationAlreadySubmittedException();
		}
		String cleanName = com.zaylope.registration.common.validation.ApplicantInputValidator
				.normalizeFullName(requestedFullName);
		if (cleanName == null) {
			throw new com.zaylope.registration.common.exception.InvalidRegistrationInputException(
					"fullName",
					com.zaylope.registration.common.validation.ApplicantInputValidator.FULL_NAME_MESSAGE);
		}
		applicant.updateFullName(cleanName);
		applicantRepository.saveAndFlush(applicant);
		return registrationState(applicantId);
	}

	@Transactional
	public ApplicantRegistrationResponse submit(long applicantId) {
		return submit(applicantId, false, null);
	}

	@Transactional
	public ApplicantRegistrationResponse submit(
			long applicantId,
			boolean consentAccepted,
			String privacyNoticeVersion) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);
		requireVerifiedApplicant(applicant);
		if (!applicant.hasValidPersonalDetails()) {
			throw new IllegalStateException("Valid personal details are required before submitting registration.");
		}
		if (applicant.getRegistrationSubmittedAt() != null) {
			throw new RegistrationAlreadySubmittedException();
		}
		if (!consentAccepted && !applicant.hasPrivacyConsent()) {
			throw new OnboardingConsentRequiredException();
		}

		Instant now = Instant.now();
		if (!applicant.hasPrivacyConsent()) {
			String version = (privacyNoticeVersion != null && !privacyNoticeVersion.isBlank())
					? privacyNoticeVersion.trim()
					: "onboarding-v1";
			applicant.recordPrivacyConsent(now, version);
		}
		applicant.markRegistrationSubmitted(now);
		applicantRepository.saveAndFlush(applicant);
		return new ApplicantRegistrationResponse("Registration submitted successfully", "SUBMITTED", "APP-" + applicant.getApplicantId());
	}

	@Scheduled(cron = "${app.documents.retention-cron:0 31 2 * * *}")
	@Transactional
	public void purgeExpiredDocuments() {
		if (!properties.isPolicyConfigured()) {
			return;
		}
		Instant cutoff = Instant.now().minus(properties.retentionDuration());
		List<ApplicantDocument> expired = documentRepository.findAllByUploadedAtBefore(cutoff);
		for (ApplicantDocument document : expired) {
			long applicantId = document.getApplicant().getApplicantId();
			String storageIdentifier = document.getStorageIdentifier();
			recordAudit(document, applicantId, SYSTEM_ROLE, "RETENTION_DELETE", Instant.now());
			documentRepository.delete(document);
			registerFileLifecycle(null, storageIdentifier);
		}
	}

	private ApplicantDocumentContent content(ApplicantDocument document) {
		return new ApplicantDocumentContent(
				storage.read(document.getStorageIdentifier()),
				document.getContentType(),
				document.getOriginalFilename());
	}

	private Applicant findEditableApplicant(long applicantId) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);
		requireVerifiedApplicant(applicant);
		if (applicant.getRegistrationSubmittedAt() != null) {
			throw new RegistrationAlreadySubmittedException();
		}
		return applicant;
	}

	private Applicant requireApplicant(long applicantId) {
		return applicantRepository.findById(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);
	}

	private static void requireVerifiedApplicant(Applicant applicant) {
		if (!isContactVerified(applicant)) {
			throw new ApplicantVerificationRequiredException();
		}
	}

	private static boolean isContactVerified(Applicant applicant) {
		return applicant.isContactVerified();
	}

	private void requireConfiguredPolicy() {
		if (!properties.isPolicyConfigured()) {
			throw new DocumentUploadNotConfiguredException();
		}
	}

	private void recordAudit(
			ApplicantDocument document,
			long applicantId,
			String actorRole,
			String action,
			Instant occurredAt) {
		auditRepository.save(new ApplicantDocumentAuditEvent(
				document.getDocumentId(),
				applicantId,
				actorRole,
				action,
				occurredAt));
	}

	private void registerFileLifecycle(String newFile, String oldFile) {
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCompletion(int status) {
				if (status == STATUS_COMMITTED) {
					if (oldFile != null) {
						storage.delete(oldFile);
					}
				} else if (newFile != null) {
					storage.delete(newFile);
				}
			}
		});
	}
}
