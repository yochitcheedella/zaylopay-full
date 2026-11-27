package com.zaylope.registration.document;

import com.zaylope.registration.applicant.Applicant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "applicant_documents")
public class ApplicantDocument {

	@Id
	@Column(name = "document_id", length = 36, nullable = false, updatable = false)
	private String documentId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "applicant_id", nullable = false)
	private Applicant applicant;

	@Column(name = "document_type", length = 80, nullable = false)
	private String documentType;

	@Column(name = "storage_identifier", length = 80, nullable = false, unique = true)
	private String storageIdentifier;

	@Column(name = "original_filename", length = 255, nullable = false)
	private String originalFilename;

	@Column(name = "content_type", length = 64, nullable = false)
	private String contentType;

	@Column(name = "file_size", nullable = false)
	private long fileSize;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	@Column(name = "consent_at", nullable = false)
	private Instant consentAt;

	@Column(name = "privacy_notice_version", length = 64, nullable = false)
	private String privacyNoticeVersion;

	@Enumerated(EnumType.STRING)
	@Column(name = "verification_status", length = 16, nullable = false)
	private ApplicantDocumentVerificationStatus verificationStatus =
			ApplicantDocumentVerificationStatus.PENDING;

	protected ApplicantDocument() {
	}

	public ApplicantDocument(
			String documentId,
			Applicant applicant,
			String documentType,
			String storageIdentifier,
			String originalFilename,
			String contentType,
			long fileSize,
			Instant uploadedAt,
			Instant consentAt,
			String privacyNoticeVersion) {
		this.documentId = documentId;
		this.applicant = applicant;
		this.documentType = documentType;
		this.storageIdentifier = storageIdentifier;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.fileSize = fileSize;
		this.uploadedAt = uploadedAt;
		this.consentAt = consentAt;
		this.privacyNoticeVersion = privacyNoticeVersion;
	}

	public void replaceFile(
			String newStorageIdentifier,
			String newOriginalFilename,
			String newContentType,
			long newFileSize,
			Instant uploadedAt,
			Instant consentAt,
			String privacyNoticeVersion) {
		storageIdentifier = newStorageIdentifier;
		originalFilename = newOriginalFilename;
		contentType = newContentType;
		fileSize = newFileSize;
		this.uploadedAt = uploadedAt;
		this.consentAt = consentAt;
		this.privacyNoticeVersion = privacyNoticeVersion;
		verificationStatus = ApplicantDocumentVerificationStatus.PENDING;
	}

	public void updateVerificationStatus(ApplicantDocumentVerificationStatus newStatus) {
		if (newStatus != null) {
			this.verificationStatus = newStatus;
		}
	}

	public String getDocumentId() {
		return documentId;
	}

	public Applicant getApplicant() {
		return applicant;
	}

	public String getDocumentType() {
		return documentType;
	}

	public String getStorageIdentifier() {
		return storageIdentifier;
	}

	public String getOriginalFilename() {
		return originalFilename;
	}

	public String getContentType() {
		return contentType;
	}

	public long getFileSize() {
		return fileSize;
	}

	public Instant getUploadedAt() {
		return uploadedAt;
	}

	public Instant getConsentAt() {
		return consentAt;
	}

	public String getPrivacyNoticeVersion() {
		return privacyNoticeVersion;
	}

	public ApplicantDocumentVerificationStatus getVerificationStatus() {
		return verificationStatus;
	}
}
