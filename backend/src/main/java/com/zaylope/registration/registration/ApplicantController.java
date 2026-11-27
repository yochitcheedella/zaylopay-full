package com.zaylope.registration.registration;

import com.zaylope.registration.session.ApplicantSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants")
public class ApplicantController {

	private final ApplicantService applicantService;
	private final ApplicantSessionService sessionService;

	public ApplicantController(
			ApplicantService applicantService,
			ApplicantSessionService sessionService) {
		this.applicantService = applicantService;
		this.sessionService = sessionService;
	}

	@PostMapping("/register")
	public ResponseEntity<ApplicantRegistrationResponse> register(
			@Valid @RequestBody ApplicantRegistrationRequest request,
			HttpServletResponse response) {
		ApplicantRegistrationResult result = applicantService.register(request);
		sessionService.issueSessionCookie(response, result.sessionToken());
		return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
	}
}
