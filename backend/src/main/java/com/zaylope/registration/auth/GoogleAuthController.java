package com.zaylope.registration.auth;

import com.zaylope.registration.auth.GoogleAuthDtos.GoogleAuthRequest;
import com.zaylope.registration.auth.GoogleAuthDtos.GoogleAuthResponse;
import com.zaylope.registration.auth.GoogleAuthDtos.GoogleCompleteProfileRequest;
import com.zaylope.registration.auth.GoogleAuthDtos.GoogleLinkVerifyRequest;
import com.zaylope.registration.common.exception.OnboardingSessionException;
import com.zaylope.registration.session.ApplicantSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {

	private final GoogleAuthService googleAuthService;
	private final ApplicantSessionService sessionService;

	public GoogleAuthController(
			GoogleAuthService googleAuthService,
			ApplicantSessionService sessionService) {
		this.googleAuthService = googleAuthService;
		this.sessionService = sessionService;
	}

	@PostMapping
	public ResponseEntity<?> authenticate(
			@Valid @RequestBody GoogleAuthRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse response) {
		String existingSessionToken = sessionService.extractApplicantToken(httpRequest);
		String existingOnboardingToken = sessionService.extractOnboardingToken(httpRequest);

		GoogleAuthService.AuthOutcome outcome = googleAuthService.authenticateWithGoogle(
				request.credential(), request.intent(), existingSessionToken, existingOnboardingToken);

		if (outcome == null) {
			ProblemDetail problem = ProblemDetail.forStatusAndDetail(
					HttpStatus.UNAUTHORIZED, "Invalid, expired, or untrusted Google credential token.");
			problem.setTitle("Google Authentication Failed");
			problem.setProperty("code", "INVALID_GOOGLE_TOKEN");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
		}

		applyCookies(outcome, response);
		return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(outcome.response());
	}

	@GetMapping("/onboarding")
	public ResponseEntity<GoogleAuthResponse> getOnboardingState(HttpServletRequest request) {
		String onboardingToken = requireOnboardingCookie(request);
		return ResponseEntity.ok(googleAuthService.getOnboardingState(onboardingToken));
	}

	@PostMapping("/complete-profile")
	public ResponseEntity<GoogleAuthResponse> completeProfile(
			@Valid @RequestBody GoogleCompleteProfileRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse response) {
		String onboardingToken = requireOnboardingCookie(httpRequest);
		String existingApplicantToken = sessionService.extractApplicantToken(httpRequest);

		GoogleAuthService.AuthOutcome outcome = googleAuthService.completeProfile(
				onboardingToken,
				request.fullName(),
				request.mobileNumber(),
				existingApplicantToken);
		applyCookies(outcome, response);
		return ResponseEntity.status(HttpStatus.CREATED).body(outcome.response());
	}

	@PostMapping("/link/send-otp")
	public ResponseEntity<Map<String, Object>> sendLinkingOtp(HttpServletRequest httpRequest) {
		String onboardingToken = requireOnboardingCookie(httpRequest);
		ApplicantAuthChallengeService.LinkOtpDispatchResult result =
				googleAuthService.sendLinkingOtp(onboardingToken);
		return ResponseEntity.ok(Map.of(
				"challengeId", result.challengeId(),
				"channel", result.channel().name(),
				"maskedDestination", result.maskedDestination(),
				"resendAfterSeconds", result.resendAfterSeconds(),
				"expiresAt", result.expiresAt(),
				"resendAvailableAt", result.resendAvailableAt(),
				"deliveryMode", result.deliveryMode()));
	}

	@PostMapping("/link/verify")
	public ResponseEntity<GoogleAuthResponse> verifyLinkingOtp(
			@Valid @RequestBody GoogleLinkVerifyRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse response) {
		String onboardingToken = requireOnboardingCookie(httpRequest);
		String existingApplicantToken = sessionService.extractApplicantToken(httpRequest);

		GoogleAuthService.AuthOutcome outcome = googleAuthService.verifyLinkingOtp(
				onboardingToken, request.otpCode(), existingApplicantToken);
		applyCookies(outcome, response);
		return ResponseEntity.ok(outcome.response());
	}

	@PostMapping("/cancel")
	public ResponseEntity<Void> cancelOnboarding(
			HttpServletRequest request,
			HttpServletResponse response) {
		String onboardingToken = sessionService.extractOnboardingToken(request);
		if (onboardingToken != null) {
			googleAuthService.cancelOnboarding(onboardingToken);
		}
		sessionService.clearOnboardingCookie(response);
		return ResponseEntity.noContent().build();
	}

	private String requireOnboardingCookie(HttpServletRequest request) {
		String token = sessionService.extractOnboardingToken(request);
		if (token == null || token.isBlank()) {
			throw new OnboardingSessionException(
					"ONBOARDING_SESSION_REQUIRED",
					"Your Google sign-in session has expired. Please continue with Google again.");
		}
		return token;
	}

	private void applyCookies(GoogleAuthService.AuthOutcome outcome, HttpServletResponse response) {
		if (outcome.clearApplicantSession()) {
			sessionService.clearSessionCookie(response);
		}
		if (outcome.clearOnboardingSession()) {
			sessionService.clearOnboardingCookie(response);
		}
		if (outcome.applicantSessionToken() != null) {
			sessionService.issueSessionCookie(response, outcome.applicantSessionToken());
		}
		if (outcome.onboardingSessionToken() != null) {
			sessionService.issueOnboardingCookie(response, outcome.onboardingSessionToken());
		}
	}
}
