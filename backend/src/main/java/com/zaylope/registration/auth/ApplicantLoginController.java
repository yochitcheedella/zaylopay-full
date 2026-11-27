package com.zaylope.registration.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.session.ApplicantAccessSessionRepository;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantContactMasker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class ApplicantLoginController {
	public static final String AUTH_CONTRACT = "verified-contact-v1";

	private final ApplicantRepository applicantRepository;
	private final ApplicantSessionService applicantSessionService;
	private final ApplicantOnboardingSessionService onboardingSessionService;
	private final ApplicantAuthChallengeService authChallengeService;
	private final AffiliateMembershipRepository membershipRepository;
	private final com.zaylope.registration.affiliate.MembershipActivationPolicy activationPolicy;
	private final com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository;

	public ApplicantLoginController(
			ApplicantRepository applicantRepository,
			ApplicantSessionService applicantSessionService,
			ApplicantOnboardingSessionService onboardingSessionService,
			ApplicantAuthChallengeService authChallengeService,
			AffiliateMembershipRepository membershipRepository) {
		this(
				applicantRepository,
				applicantSessionService,
				onboardingSessionService,
				authChallengeService,
				membershipRepository,
				new com.zaylope.registration.affiliate.MembershipActivationPolicy(),
				null);
	}

	public ApplicantLoginController(
			ApplicantRepository applicantRepository,
			ApplicantSessionService applicantSessionService,
			ApplicantOnboardingSessionService onboardingSessionService,
			ApplicantAuthChallengeService authChallengeService,
			AffiliateMembershipRepository membershipRepository,
			com.zaylope.registration.affiliate.MembershipActivationPolicy activationPolicy) {
		this(
				applicantRepository,
				applicantSessionService,
				onboardingSessionService,
				authChallengeService,
				membershipRepository,
				activationPolicy,
				null);
	}

	@org.springframework.beans.factory.annotation.Autowired
	public ApplicantLoginController(
			ApplicantRepository applicantRepository,
			ApplicantSessionService applicantSessionService,
			ApplicantOnboardingSessionService onboardingSessionService,
			ApplicantAuthChallengeService authChallengeService,
			AffiliateMembershipRepository membershipRepository,
			com.zaylope.registration.affiliate.MembershipActivationPolicy activationPolicy,
			com.zaylope.registration.payment.PaymentOrderRepository paymentOrderRepository) {
		this.applicantRepository = applicantRepository;
		this.applicantSessionService = applicantSessionService;
		this.onboardingSessionService = onboardingSessionService;
		this.authChallengeService = authChallengeService;
		this.membershipRepository = membershipRepository;
		this.activationPolicy = activationPolicy;
		this.paymentOrderRepository = paymentOrderRepository;
	}

	@PostMapping("/login/otp/request")
	public ResponseEntity<LoginOtpRequestResponse> requestLoginOtp(
			@Valid @RequestBody LoginOtpRequest request) {
		return requestOtp(request, false);
	}

	@PostMapping("/recovery/otp/request")
	public ResponseEntity<LoginOtpRequestResponse> requestRegistrationRecoveryOtp(
			@Valid @RequestBody LoginOtpRequest request) {
		return requestOtp(request, true);
	}

	private ResponseEntity<LoginOtpRequestResponse> requestOtp(LoginOtpRequest request, boolean recovery) {
		LoginOtpResponseTiming timing = LoginOtpResponseTiming.start();
		try {
			ApplicantAuthChallengeService.LoginOtpRequestResult result =
					recovery ? authChallengeService.requestRegistrationRecoveryOtp(request.identifier())
							: authChallengeService.requestLoginOtp(request.identifier());
			return ResponseEntity.ok(new LoginOtpRequestResponse(
					result.accepted(),
					result.challengeId(),
					result.resendAfterSeconds(),
					result.expiresInSeconds(),
					result.serverTime(),
					result.expiresAt(),
					result.resendAvailableAt(),
					result.message(), result.outcome()));
		} finally {
			// The service proxy has completed its transaction before waiting here.
			timing.awaitDeadline();
		}
	}

	@PostMapping("/login/otp/verify")
	public ResponseEntity<LoginOtpVerifyResponse> verifyLoginOtp(
			@Valid @RequestBody LoginOtpVerifyRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse response) {
		return verifyOtp(request, httpRequest, response, false);
	}

	@PostMapping("/recovery/otp/verify")
	public ResponseEntity<LoginOtpVerifyResponse> verifyRegistrationRecoveryOtp(
			@Valid @RequestBody LoginOtpVerifyRequest request, HttpServletRequest httpRequest,
			HttpServletResponse response) {
		return verifyOtp(request, httpRequest, response, true);
	}

	private ResponseEntity<LoginOtpVerifyResponse> verifyOtp(LoginOtpVerifyRequest request,
			HttpServletRequest httpRequest, HttpServletResponse response, boolean recovery) {
		String currentToken = applicantSessionService.extractApplicantToken(httpRequest);
		ApplicantAuthChallengeService.LoginOtpVerifyResult result = recovery
				? authChallengeService.verifyRegistrationRecoveryOtp(request.challengeId(), request.identifier(), request.code(), currentToken)
				: authChallengeService.verifyLoginOtp(request.challengeId(), request.identifier(), request.code(), currentToken);

		applicantSessionService.issueSessionCookie(response, result.sessionToken());
		applicantSessionService.clearOnboardingCookie(response);

		Applicant applicant = result.applicant();
		return ResponseEntity.ok(new LoginOtpVerifyResponse(
				true,
				result.sessionScope().name(),
				resolveEligibleStep(applicant),
				applicant.getFullName(),
				ApplicantContactMasker.maskMobile(applicant.getMobileNumber()),
				ApplicantContactMasker.maskEmail(applicant.getEmail()),
				applicant.isMobileVerified(),
				applicant.isTrustedEmailVerified(),
				applicant.getEmailVerificationMethod(),
				result.draftRecovered()));
	}

	@GetMapping("/session")
	public ResponseEntity<Map<String, Object>> session(HttpServletRequest request, HttpServletResponse response) {
		response.setHeader("X-Zaylope-Auth-Contract", AUTH_CONTRACT);
		String onboardingToken = applicantSessionService.extractOnboardingToken(request);
		Optional<ApplicantOnboardingSession> onboardingOpt = onboardingToken != null
				? onboardingSessionService.findActive(onboardingToken)
				: Optional.empty();

		String token = applicantSessionService.extractApplicantToken(request);
		if (token == null || token.isBlank()) {
			return ResponseEntity.ok(anonymousSessionResponse("NONE", onboardingOpt.orElse(null)));
		}

		Optional<ApplicantAccessSessionRepository.ActiveSessionProjection> sessionOpt =
				applicantSessionService.findActiveSession(token);
		if (sessionOpt.isEmpty()) {
			return ResponseEntity.ok(anonymousSessionResponse("EXPIRED_OR_INVALID", onboardingOpt.orElse(null)));
		}

		Applicant applicant = applicantRepository.findById(sessionOpt.get().applicantId()).orElse(null);
		if (applicant == null) {
			return ResponseEntity.ok(anonymousSessionResponse("EXPIRED_OR_INVALID", onboardingOpt.orElse(null)));
		}

		ApplicantSessionScope scope = sessionOpt.get().sessionScope();
		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(applicant.getApplicantId());
		boolean restricted = activationPolicy.isAccountRestricted(applicant, membershipOpt);
		boolean activeAffiliate = activationPolicy.canAccessMembershipBenefits(applicant, membershipOpt);
		String eligibleStep = resolveEligibleStep(applicant, membershipOpt, activeAffiliate, restricted);
		String affiliateId = activeAffiliate ? membershipOpt.get().getAffiliateId() : null;

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("authenticated", true);
		// Keep the legacy applicant-capability flag for registration restoration, and expose identity proof separately.
		data.put("identityAuthenticated", scope == ApplicantSessionScope.AUTHENTICATED && applicant.isContactVerified());
		data.put("sessionState", "ACTIVE");
		data.put("sessionScope", scope.name());
		data.put("eligibleStep", eligibleStep);
		data.put("canAccessDashboard", activeAffiliate && scope == ApplicantSessionScope.AUTHENTICATED);
		data.put("applicantReference", applicant.getRegistrationId() != null
				? applicant.getRegistrationId()
				: (affiliateId != null ? affiliateId : "APP-" + applicant.getApplicantId()));
		if (applicant.getFullName() != null) {
			data.put("fullName", applicant.getFullName());
		}
		data.put("maskedMobileNumber", ApplicantContactMasker.maskMobile(applicant.getMobileNumber()));
		data.put("maskedEmailAddress", ApplicantContactMasker.maskEmail(applicant.getEmail()));
		data.put("mobileVerified", applicant.isMobileVerified());
		data.put("emailVerified", applicant.isTrustedEmailVerified());
		data.put("legacyEmailUnverified", applicant.isEmailVerified() && !applicant.isTrustedEmailVerified());
		data.put("emailVerificationMethod", applicant.getEmailVerificationMethod());
		data.put("contactVerified", applicant.isContactVerified());
		data.put("registrationSubmitted", applicant.isRegistrationSubmitted());
		data.put("paymentStatus", applicant.getPaymentStatus());
		data.put("approvalStatus", applicant.getApprovalStatus());
		data.put("membershipStatus", restricted
				? "REVOKED"
				: activeAffiliate
						? "ACTIVE"
						: applicant.isPaymentPaid() ? "PENDING_ACTIVATION" : "NOT_STARTED");
		data.put("activationPending", !restricted && applicant.isPaymentPaid() && !activeAffiliate);
		if (affiliateId != null) {
			data.put("affiliateId", affiliateId);
			data.put("activatedAt", membershipOpt.get().getActivatedAt());
		}
		if (onboardingOpt.isPresent()) {
			data.put("onboarding", buildOnboardingSummary(onboardingOpt.get()));
		}

		return ResponseEntity.ok(data);
	}

	@GetMapping("/me")
	public ApplicantMeResponse me(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal) {
		Applicant applicant = applicantRepository.findById(principal.applicantId())
				.orElseThrow(ApplicantNotFoundException::new);
		return new ApplicantMeResponse(
				"APP-" + applicant.getApplicantId(),
				applicant.getFullName(),
				ApplicantContactMasker.maskMobile(applicant.getMobileNumber()),
				ApplicantContactMasker.maskEmail(applicant.getEmail()),
				applicant.isMobileVerified(),
				applicant.isTrustedEmailVerified(),
				principal.sessionScope().name(),
				resolveEligibleStep(applicant));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(
			HttpServletRequest request,
			HttpServletResponse response) {
		String token = applicantSessionService.extractApplicantToken(request);
		if (token != null) {
			applicantSessionService.revoke(token);
		}
		String onboardingToken = applicantSessionService.extractOnboardingToken(request);
		if (onboardingToken != null) {
			onboardingSessionService.revoke(onboardingToken);
		}
		applicantSessionService.clearSessionCookie(response);
		applicantSessionService.clearOnboardingCookie(response);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout-all")
	public ResponseEntity<Void> logoutAll(
			@RequestAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE) ApplicantSessionPrincipal principal,
			HttpServletRequest request,
			HttpServletResponse response) {
		applicantSessionService.revokeAll(principal.applicantId());
		String onboardingToken = applicantSessionService.extractOnboardingToken(request);
		if (onboardingToken != null) {
			onboardingSessionService.revoke(onboardingToken);
		}
		applicantSessionService.clearSessionCookie(response);
		applicantSessionService.clearOnboardingCookie(response);
		return ResponseEntity.noContent().build();
	}

	private Map<String, Object> anonymousSessionResponse(String sessionState, ApplicantOnboardingSession onboarding) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("authenticated", false);
		data.put("identityAuthenticated", false);
		data.put("sessionState", sessionState);
		data.put("canAccessDashboard", false);
		if (onboarding != null) {
			data.put("onboarding", buildOnboardingSummary(onboarding));
		}
		return data;
	}

	private Map<String, Object> buildOnboardingSummary(ApplicantOnboardingSession session) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("active", true);
		map.put("flowType", session.getFlowType().name());
		map.put("email", session.getGoogleEmail());
		if (session.getGoogleName() != null) {
			map.put("fullName", session.getGoogleName());
		}
		String pic = session.getGooglePictureUrl();
		if (pic != null && (pic.startsWith("https://") || pic.startsWith("http://"))) {
			map.put("pictureUrl", pic);
		}
		return map;
	}

	private String resolveEligibleStep(Applicant applicant) {
		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(applicant.getApplicantId());
		boolean restricted = activationPolicy.isAccountRestricted(applicant, membershipOpt);
		boolean activeAffiliate = activationPolicy.canAccessMembershipBenefits(applicant, membershipOpt);
		return resolveEligibleStep(applicant, membershipOpt, activeAffiliate, restricted);
	}

	private String resolveEligibleStep(
			Applicant applicant,
			Optional<AffiliateMembership> membershipOpt,
			boolean activeAffiliate,
			boolean restricted) {
		boolean hasPendingOrder = !activeAffiliate
				&& !restricted
				&& !applicant.isPaymentPaid()
				&& applicant.getApplicantId() != null
				&& paymentOrderRepository != null
				&& paymentOrderRepository
						.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(applicant.getApplicantId(), "CREATED")
						.isPresent();
		return activationPolicy.resolveDestination(applicant, membershipOpt, hasPendingOrder);
	}

	public record LoginOtpRequest(
			@NotBlank(message = com.zaylope.registration.common.validation.ApplicantInputValidator.LOGIN_IDENTIFIER_MESSAGE)
			@Size(max = 254, message = com.zaylope.registration.common.validation.ApplicantInputValidator.LOGIN_IDENTIFIER_MESSAGE)
			@com.zaylope.registration.common.validation.ValidLoginIdentifier
			String identifier) {}

	public record LoginOtpRequestResponse(
			boolean accepted,
			String challengeId,
			long resendAfterSeconds,
			long expiresInSeconds,
			Instant serverTime,
			Instant expiresAt,
			Instant resendAvailableAt,
			String message, String outcome) {}

	public record LoginOtpVerifyRequest(
			@NotBlank(message = "Challenge reference is required.")
			String challengeId,
			@NotBlank(message = com.zaylope.registration.common.validation.ApplicantInputValidator.LOGIN_IDENTIFIER_MESSAGE)
			@Size(max = 254, message = com.zaylope.registration.common.validation.ApplicantInputValidator.LOGIN_IDENTIFIER_MESSAGE)
			@com.zaylope.registration.common.validation.ValidLoginIdentifier
			String identifier,
			@NotBlank(message = "Verification code is required.")
			@Pattern(regexp = "[0-9]{6}", message = "Enter a valid 6-digit verification code.")
			String code) {}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record LoginOtpVerifyResponse(
			boolean authenticated,
			String sessionScope,
			String eligibleStep,
			String fullName,
			String maskedMobileNumber,
			String maskedEmailAddress,
			boolean mobileVerified,
			boolean emailVerified,
			String emailVerificationMethod,
			boolean draftRecovered) {}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record ApplicantMeResponse(
			String applicantReference,
			String fullName,
			String maskedMobileNumber,
			String maskedEmailAddress,
			boolean mobileVerified,
			boolean emailVerified,
			String sessionScope,
			String eligibleStep) {}
}
