package com.zaylope.registration.document;

import com.zaylope.registration.config.DocumentUploadProperties.DocumentTypeOption;
import com.zaylope.registration.config.DocumentUploadProperties;
import java.util.List;

public record ApplicantDocumentPolicyResponse(
		String privacyNotice,
		String privacyNoticeVersion,
		String collectionPurpose,
		String authorizedPersonnel,
		String retentionPolicy,
		String retentionPeriod,
		String deletionPolicy,
		List<String> acceptedFileFormats,
		long maximumFileSizeBytes,
		List<DocumentTypeOption> documentTypes) {
}
