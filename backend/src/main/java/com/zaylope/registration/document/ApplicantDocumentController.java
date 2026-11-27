package com.zaylope.registration.document;

import com.zaylope.registration.session.ApplicantSessionPrincipal;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/applicants/me/documents")
public class ApplicantDocumentController {

	private final ApplicantDocumentService documentService;

	public ApplicantDocumentController(ApplicantDocumentService documentService) {
		this.documentService = documentService;
	}

	@GetMapping("/policy")
	public ApplicantDocumentPolicyResponse policy(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		return documentService.policy(principal.applicantId());
	}

	@GetMapping
	public List<ApplicantDocumentResponse> list(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		return documentService.list(principal.applicantId());
	}

	@PutMapping("/{documentType}")
	public ApplicantDocumentResponse uploadOrReplace(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@PathVariable @NotBlank String documentType,
			@RequestPart("file") MultipartFile file,
			@RequestParam("consent") boolean consent) {
		return documentService.uploadOrReplace(principal.applicantId(), documentType, file, consent);
	}

	@DeleteMapping("/{documentId}")
	public ResponseEntity<Void> remove(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@PathVariable @NotBlank String documentId) {
		documentService.remove(principal.applicantId(), documentId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{documentId}/content")
	public ResponseEntity<byte[]> content(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@PathVariable @NotBlank String documentId) {
		return documentResponse(documentService.applicantContent(principal.applicantId(), documentId));
	}

	static ResponseEntity<byte[]> documentResponse(ApplicantDocumentContent content) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(content.contentType()));
		headers.setContentDisposition(ContentDisposition.inline().filename(content.filename()).build());
		headers.setCacheControl(CacheControl.noStore());
		headers.set("X-Content-Type-Options", "nosniff");
		return ResponseEntity.ok().headers(headers).body(content.content());
	}
}
