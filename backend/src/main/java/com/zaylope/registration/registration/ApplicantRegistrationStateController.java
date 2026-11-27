package com.zaylope.registration.registration;

import com.zaylope.registration.common.validation.ApplicantInputValidator;
import com.zaylope.registration.common.validation.ValidFullName;
import com.zaylope.registration.document.ApplicantDocumentService;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants/me/registration")
public class ApplicantRegistrationStateController {

	private final ApplicantDocumentService documentService;

	public ApplicantRegistrationStateController(ApplicantDocumentService documentService) {
		this.documentService = documentService;
	}

	@GetMapping
	public ApplicantRegistrationStateResponse state(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		return documentService.registrationState(principal.applicantId());
	}

	@PutMapping("/profile")
	public ApplicantRegistrationStateResponse updateProfile(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody UpdateProfileRequest request) {
		return documentService.updateFullName(principal.applicantId(), request.fullName());
	}

	public record UpdateProfileRequest(
			@NotBlank(message = ApplicantInputValidator.FULL_NAME_MESSAGE)
			@Size(max = ApplicantInputValidator.MAX_FULL_NAME_LENGTH,
					message = ApplicantInputValidator.FULL_NAME_MESSAGE)
			@ValidFullName
			String fullName) {
	}
}
