package com.zaylope.registration.verification;

import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applicants/me/verification")
public class ApplicantVerificationController {

	private final ApplicantVerificationService verificationService;
	private final ApplicantSessionService sessionService;
	private final ApplicantRepository applicantRepository;

	@Autowired
	public ApplicantVerificationController(
			ApplicantVerificationService verificationService,
			ApplicantSessionService sessionService,
			ApplicantRepository applicantRepository) {
		this.verificationService = verificationService;
		this.sessionService = sessionService;
		this.applicantRepository = applicantRepository;
	}

	public ApplicantVerificationController(ApplicantVerificationService verificationService) {
		this(verificationService, null, null);
	}

	@GetMapping("/status")
	public VerificationStateResponse status(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		return verificationService.verificationState(principal.applicantId());
	}

	@PostMapping("/switch")
	public VerificationStateResponse switchChannel(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody VerificationChannelSwitchRequest request) {
		return verificationService.switchChannel(principal.applicantId(), request.channel());
	}

	@PostMapping("/mobile/send")
	public OtpDispatchResponse sendMobileCode(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		return verificationService.sendMobileCode(principal.applicantId());
	}

	@PutMapping("/mobile")
	public MobileNumberChangeResponse changeMobileNumber(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody MobileNumberChangeRequest request) {
		return verificationService.changeMobileNumber(principal.applicantId(), request.mobileNumber());
	}

	@PostMapping("/mobile/verify")
	public ApplicantVerificationStatusResponse verifyMobileCode(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody OtpCodeRequest request,
			HttpServletResponse response) {
		ApplicantVerificationStatusResponse status =
				verificationService.verifyMobileCode(principal.applicantId(), request.code());
		rotateToAuthenticatedIfUpgraded(principal, status, response);
		return status;
	}

	@PostMapping("/email/send")
	public OtpDispatchResponse sendEmailCode(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		return verificationService.sendEmailCode(principal.applicantId());
	}

	@PutMapping("/email")
	public ApplicantVerificationStatusResponse changeEmail(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody EmailAddressChangeRequest request) {
		return verificationService.changeEmailAddress(principal.applicantId(), request.email());
	}

	@PostMapping("/email/verify")
	public ApplicantVerificationStatusResponse verifyEmailCode(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			@Valid @RequestBody OtpCodeRequest request,
			HttpServletResponse response) {
		ApplicantVerificationStatusResponse status =
				verificationService.verifyEmailCode(principal.applicantId(), request.code());
		rotateToAuthenticatedIfUpgraded(principal, status, response);
		return status;
	}

	private void rotateToAuthenticatedIfUpgraded(
			ApplicantSessionPrincipal principal,
			ApplicantVerificationStatusResponse status,
			HttpServletResponse response) {
		if (sessionService == null || applicantRepository == null) {
			return;
		}
		if (status.contactVerified() && principal.sessionScope() == ApplicantSessionScope.ONBOARDING) {
			applicantRepository.findById(principal.applicantId()).ifPresent(applicant -> {
				String rotated = sessionService.rotateSession(
						applicant, ApplicantSessionScope.AUTHENTICATED, principal.sessionToken());
				sessionService.issueSessionCookie(response, rotated);
			});
		}
	}
}
