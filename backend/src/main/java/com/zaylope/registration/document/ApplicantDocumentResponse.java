package com.zaylope.registration.document;

import java.time.Instant;

public record ApplicantDocumentResponse(
		String documentId,
		String documentType,
		String originalFilename,
		String contentType,
		long fileSize,
		Instant uploadedAt,
		String verificationStatus) {

	public static ApplicantDocumentResponse from(ApplicantDocument document) {
		return new ApplicantDocumentResponse(
				document.getDocumentId(),
				document.getDocumentType(),
				document.getOriginalFilename(),
				document.getContentType(),
				document.getFileSize(),
				document.getUploadedAt(),
				document.getVerificationStatus().name());
	}
}
