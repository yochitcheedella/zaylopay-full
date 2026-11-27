package com.zaylope.registration.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zaylope.registration.common.validation.ApplicantInputValidator;
import com.zaylope.registration.common.validation.ValidFullName;
import com.zaylope.registration.common.validation.ValidIndianMobile;
import com.zaylope.registration.registration.IndianMobileNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class GoogleAuthDtos {

	public record GoogleAuthRequest(
			@NotBlank(message = "Google credential token is required")
			String credential,
			String intent // "LOGIN" | "REGISTER" (defaults to REGISTER when omitted)
	) {
		public GoogleAuthRequest(String credential) {
			this(credential, "REGISTER");
		}
	}

	public record GoogleCompleteProfileRequest(
			@NotBlank(message = ApplicantInputValidator.FULL_NAME_MESSAGE)
			@Size(max = ApplicantInputValidator.MAX_FULL_NAME_LENGTH,
					message = ApplicantInputValidator.FULL_NAME_MESSAGE)
			@ValidFullName
			String fullName,
			@NotBlank(message = ApplicantInputValidator.MOBILE_MESSAGE)
			@Size(max = IndianMobileNumber.MAX_INPUT_LENGTH,
					message = ApplicantInputValidator.MOBILE_MESSAGE)
			@ValidIndianMobile
			String mobileNumber
	) {}

	public record GoogleLinkVerifyRequest(
			@NotBlank(message = "Verification code is required")
			@Pattern(regexp = "[0-9]{6}", message = "Enter a valid 6-digit verification code.")
			String otpCode
	) {}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record GoogleAuthResponse(
			String status, // "AUTHENTICATED", "NEEDS_PROFILE_COMPLETION", "LINKING_CONFIRMATION_REQUIRED"
			String applicantReference,
			String fullName,
			String maskedMobileNumber,
			String maskedEmailAddress,
			String email,
			boolean mobileVerified,
			boolean emailVerified,
			String emailVerificationMethod,
			boolean authoritativeEmail,
			boolean registrationSubmitted,
			String paymentStatus,
			String approvalStatus,
			String affiliateId,
			String eligibleStep, // "ACCOUNT", "VERIFY", "PAYMENT", "PAYMENT_STATUS", "ACTIVATION_RECOVERY", "DASHBOARD", "RESTRICTED", "REGISTER"
			String linkingChannel, // "MOBILE" | "EMAIL"
			String message,
			String pictureUrl
	) {
		public static GoogleAuthResponse accountNotFound(
				String email,
				String message,
				String pictureUrl) {
			return new GoogleAuthResponse(
					"ACCOUNT_NOT_FOUND",
					null,
					null,
					null,
					null,
					email,
					false,
					true,
					"NONE",
					true,
					false,
					null,
					null,
					null,
					"REGISTER",
					null,
					message,
					pictureUrl
			);
		}
		public static GoogleAuthResponse authenticated(
				String applicantReference,
				String fullName,
				String maskedMobileNumber,
				String maskedEmailAddress,
				boolean mobileVerified,
				boolean emailVerified,
				String emailVerificationMethod,
				boolean registrationSubmitted,
				String paymentStatus,
				String approvalStatus,
				String affiliateId,
				String eligibleStep) {
			return authenticated(
					applicantReference,
					fullName,
					maskedMobileNumber,
					maskedEmailAddress,
					mobileVerified,
					emailVerified,
					emailVerificationMethod,
					registrationSubmitted,
					paymentStatus,
					approvalStatus,
					affiliateId,
					eligibleStep,
					null);
		}

		public static GoogleAuthResponse authenticated(
				String applicantReference,
				String fullName,
				String maskedMobileNumber,
				String maskedEmailAddress,
				boolean mobileVerified,
				boolean emailVerified,
				String emailVerificationMethod,
				boolean registrationSubmitted,
				String paymentStatus,
				String approvalStatus,
				String affiliateId,
				String eligibleStep,
				String pictureUrl) {
			return new GoogleAuthResponse(
					"AUTHENTICATED",
					applicantReference,
					fullName,
					maskedMobileNumber,
					maskedEmailAddress,
					null,
					mobileVerified,
					emailVerified,
					emailVerificationMethod,
					"GOOGLE".equals(emailVerificationMethod),
					registrationSubmitted,
					paymentStatus,
					approvalStatus,
					affiliateId,
					eligibleStep,
					null,
					"Authenticated successfully",
					pictureUrl
			);
		}

		public static GoogleAuthResponse needsProfileCompletion(
				String email,
				String fullName,
				boolean emailVerified,
				boolean authoritativeEmail,
				String message) {
			return needsProfileCompletion(email, fullName, emailVerified, authoritativeEmail, message, null);
		}

		public static GoogleAuthResponse needsProfileCompletion(
				String email,
				String fullName,
				boolean emailVerified,
				boolean authoritativeEmail,
				String message,
				String pictureUrl) {
			return new GoogleAuthResponse(
					"NEEDS_PROFILE_COMPLETION",
					null,
					fullName,
					null,
					null,
					email,
					false,
					emailVerified,
					authoritativeEmail ? "GOOGLE" : "NONE",
					authoritativeEmail,
					false,
					null,
					null,
					null,
					"ACCOUNT",
					null,
					message,
					pictureUrl
			);
		}

		public static GoogleAuthResponse linkingRequired(
				String email,
				String linkingChannel,
				String maskedTarget,
				String message) {
			return linkingRequired(email, linkingChannel, maskedTarget, message, null);
		}

		public static GoogleAuthResponse linkingRequired(
				String email,
				String linkingChannel,
				String maskedTarget,
				String message,
				String pictureUrl) {
			return new GoogleAuthResponse(
					"LINKING_CONFIRMATION_REQUIRED",
					null,
					null,
					"MOBILE".equals(linkingChannel) ? maskedTarget : null,
					"EMAIL".equals(linkingChannel) ? maskedTarget : null,
					email,
					false,
					false,
					null,
					false,
					false,
					null,
					null,
					null,
					"CONFIRM_LINK",
					linkingChannel,
					message,
					pictureUrl
			);
		}
	}
}
