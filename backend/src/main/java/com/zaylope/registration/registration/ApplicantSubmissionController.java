package com.zaylope.registration.registration;

import com.zaylope.registration.document.ApplicantDocumentService;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants/me")
public class ApplicantSubmissionController {

	private final ApplicantDocumentService documentService;

	public ApplicantSubmissionController(ApplicantDocumentService documentService) {
		this.documentService = documentService;
	}

	@PostMapping("/submit")
	public ResponseEntity<ApplicantRegistrationResponse> submit(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@RequestBody(required = false) ApplicantSubmissionRequest request) {
		boolean consentAccepted = request != null && Boolean.TRUE.equals(request.consentAccepted());
		String privacyNoticeVersion = request != null ? request.privacyNoticeVersion() : null;
		return ResponseEntity.ok(documentService.submit(principal.applicantId(), consentAccepted, privacyNoticeVersion));
	}
}
