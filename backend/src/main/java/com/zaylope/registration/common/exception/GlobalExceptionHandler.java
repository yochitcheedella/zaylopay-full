package com.zaylope.registration.common.exception;

import com.zaylope.registration.verification.ApplicantAlreadyVerifiedException;
import com.zaylope.registration.verification.ApplicantVerificationChannel;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import com.zaylope.registration.verification.InvalidOtpException;
import com.zaylope.registration.verification.MobileVerificationRequiredException;
import com.zaylope.registration.verification.MobileNumberChangeRateLimitException;
import com.zaylope.registration.verification.OtpAlreadyUsedException;
import com.zaylope.registration.verification.OtpAttemptLimitExceededException;
import com.zaylope.registration.verification.OtpChallengeNotFoundException;
import com.zaylope.registration.verification.OtpExpiredException;
import com.zaylope.registration.verification.OtpRateLimitException;
import com.zaylope.registration.verification.VerificationDeliveryUnavailableException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.zaylope.registration.verification.ContactUpdateConflictException.class)
    public ResponseEntity<ProblemDetail> handleContactUpdateConflict(
            com.zaylope.registration.verification.ContactUpdateConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(), "CONTACT_UPDATE_CONFLICT");
    }
    @ExceptionHandler(com.zaylope.registration.verification.ContactReauthenticationRequiredException.class)
    public ResponseEntity<ProblemDetail> handleContactReauthentication(
            com.zaylope.registration.verification.ContactReauthenticationRequiredException exception) {
        return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "CONTACT_REAUTHENTICATION_REQUIRED");
    }
    @ExceptionHandler(com.zaylope.registration.common.validation.EmailDomainUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleEmailDomainUnavailable(
            com.zaylope.registration.common.validation.EmailDomainUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), "EMAIL_DOMAIN_CHECK_UNAVAILABLE");
    }

	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
		List<Map<String, String>> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> Map.of(
						"field", error.getField(),
						"code", "INVALID",
						"message", error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage()))
				.toList();

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
		problem.setTitle("Validation failed");
		problem.setProperty("code", "VALIDATION_FAILED");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ProblemDetail> handleUnreadableRequest(HttpMessageNotReadableException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "The request body is missing or malformed.");
		problem.setTitle("Invalid request");
		problem.setProperty("code", "INVALID_REQUEST");
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ProblemDetail> handleDataConflict(DataIntegrityViolationException exception) {
		LOGGER.warn("Applicant registration conflicted with a database constraint");
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "The registration conflicts with existing data.");
		problem.setTitle("Registration conflict");
		problem.setProperty("code", "REGISTRATION_CONFLICT");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(DataAccessResourceFailureException.class)
	public ResponseEntity<ProblemDetail> handleDatabaseUnavailable(
			DataAccessResourceFailureException exception) {
		LOGGER.error("The database resource is unavailable", exception);
		return problem(
				HttpStatus.SERVICE_UNAVAILABLE,
				"The request could not be completed because the database is unavailable.",
				"DATABASE_UNAVAILABLE");
	}

	@ExceptionHandler(CannotCreateTransactionException.class)
	public ResponseEntity<ProblemDetail> handleTransactionUnavailable(
			CannotCreateTransactionException exception) {
		LOGGER.error("A database transaction could not be started", exception);
		return problem(
				HttpStatus.SERVICE_UNAVAILABLE,
				"The request could not be completed because the database is unavailable.",
				"DATABASE_UNAVAILABLE");
	}

	@ExceptionHandler(InvalidDataAccessResourceUsageException.class)
	public ResponseEntity<ProblemDetail> handleDatabaseSchemaMismatch(
			InvalidDataAccessResourceUsageException exception) {
		LOGGER.error("The database schema or SQL is incompatible with the application", exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"The database schema is incompatible with this request.",
				"DATABASE_CONFIGURATION_ERROR");
	}

	@ExceptionHandler(DataAccessException.class)
	public ResponseEntity<ProblemDetail> handleDatabaseOperationFailure(DataAccessException exception) {
		LOGGER.error("A database operation failed", exception);
		return problem(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"The database operation could not be completed.",
				"DATABASE_OPERATION_FAILED");
	}

	@ExceptionHandler(InvalidMobileNumberException.class)
	public ResponseEntity<ProblemDetail> handleInvalidMobileNumber(InvalidMobileNumberException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "Enter a valid 10-digit mobile number.");
		problem.setTitle("Invalid mobile number");
		problem.setProperty("code", "INVALID_MOBILE_NUMBER");
		problem.setProperty("errors", List.of(Map.of(
				"field", "mobileNumber",
				"code", "INVALID",
				"message", "Enter a valid 10-digit mobile number.")));
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(InvalidRegistrationInputException.class)
	public ResponseEntity<ProblemDetail> handleInvalidRegistrationInput(InvalidRegistrationInputException exception) {
		String message = exception.getMessage() == null ? "Invalid input." : exception.getMessage();
		String field = exception.getField() == null ? "input" : exception.getField();
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
		problem.setTitle("Validation failed");
		problem.setProperty("code", "VALIDATION_FAILED");
		problem.setProperty("errors", List.of(Map.of(
				"field", field,
				"code", "INVALID",
				"message", message)));
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(ApplicantNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleApplicantNotFound(ApplicantNotFoundException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Applicant was not found.");
		problem.setTitle("Applicant not found");
		problem.setProperty("code", "APPLICANT_NOT_FOUND");
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(MobileAlreadyVerifiedException.class)
	public ResponseEntity<ProblemDetail> handleMobileAlreadyVerified(MobileAlreadyVerifiedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "This mobile number has already been verified.");
		problem.setTitle("Mobile already verified");
		problem.setProperty("code", "MOBILE_ALREADY_VERIFIED");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(MobileNumberAlreadyUsedException.class)
	public ResponseEntity<ProblemDetail> handleMobileNumberAlreadyUsed(
			MobileNumberAlreadyUsedException exception) {
		return problem(
				HttpStatus.CONFLICT,
				"This mobile number is already associated with another application.",
				"MOBILE_NUMBER_ALREADY_IN_USE");
	}

	@ExceptionHandler(DuplicateEmailException.class)
	public ResponseEntity<ProblemDetail> handleDuplicateEmail(DuplicateEmailException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.CONFLICT, "This email address is already registered.");
		problem.setTitle("Email already registered");
		problem.setProperty("code", "EMAIL_ALREADY_REGISTERED");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(DocumentUploadNotConfiguredException.class)
	public ResponseEntity<ProblemDetail> handleDocumentUploadNotConfigured(
			DocumentUploadNotConfiguredException exception) {
		return problem(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Document collection is unavailable until its privacy and storage policy is configured.",
				"DOCUMENT_UPLOAD_NOT_CONFIGURED");
	}

	@ExceptionHandler(InvalidApplicantDocumentException.class)
	public ResponseEntity<ProblemDetail> handleInvalidApplicantDocument(
			InvalidApplicantDocumentException exception) {
		return problem(
				HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage(),
				"INVALID_DOCUMENT");
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ProblemDetail> handleMaximumUploadSizeExceeded(
			MaxUploadSizeExceededException exception) {
		return problem(
				HttpStatus.CONTENT_TOO_LARGE,
				"The file exceeds the 10 MB upload limit.",
				"INVALID_DOCUMENT");
	}

	@ExceptionHandler(DocumentConsentRequiredException.class)
	public ResponseEntity<ProblemDetail> handleDocumentConsentRequired(
			DocumentConsentRequiredException exception) {
		return problem(
				HttpStatus.UNPROCESSABLE_CONTENT,
				"Confirm the configured privacy notice before uploading a document.",
				"DOCUMENT_CONSENT_REQUIRED");
	}

	@ExceptionHandler(OnboardingConsentRequiredException.class)
	public ResponseEntity<ProblemDetail> handleOnboardingConsentRequired(
			OnboardingConsentRequiredException exception) {
		return problem(
				HttpStatus.UNPROCESSABLE_CONTENT,
				exception.getMessage(),
				"ONBOARDING_CONSENT_REQUIRED");
	}

	@ExceptionHandler(RegistrationAlreadySubmittedException.class)
	public ResponseEntity<ProblemDetail> handleRegistrationAlreadySubmitted(
			RegistrationAlreadySubmittedException exception) {
		return problem(
				HttpStatus.CONFLICT,
				"This registration has already been submitted.",
				"REGISTRATION_ALREADY_SUBMITTED");
	}

	@ExceptionHandler(RequiredDocumentsMissingException.class)
	public ResponseEntity<ProblemDetail> handleRequiredDocumentsMissing(
			RequiredDocumentsMissingException exception) {
		return problem(
				HttpStatus.CONFLICT,
				"Upload each required document before submitting registration.",
				"REQUIRED_DOCUMENTS_MISSING");
	}

	@ExceptionHandler(ApplicantDocumentNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleApplicantDocumentNotFound(
			ApplicantDocumentNotFoundException exception) {
		return problem(
				HttpStatus.NOT_FOUND,
				"The requested document was not found.",
				"DOCUMENT_NOT_FOUND");
	}

	@ExceptionHandler(PrivateDocumentStorageException.class)
	public ResponseEntity<ProblemDetail> handlePrivateDocumentStorage(
			PrivateDocumentStorageException exception) {
		return problem(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Private document storage is temporarily unavailable.",
				"DOCUMENT_STORAGE_UNAVAILABLE");
	}

	@ExceptionHandler(VerificationDeliveryUnavailableException.class)
	public ResponseEntity<ProblemDetail> handleVerificationDeliveryUnavailable(
			VerificationDeliveryUnavailableException exception) {
		return problem(
				HttpStatus.SERVICE_UNAVAILABLE,
				"Verification code delivery is temporarily unavailable. Please try again shortly.",
				"VERIFICATION_DELIVERY_UNAVAILABLE");
	}

	@ExceptionHandler(ApplicantVerificationRequiredException.class)
	public ResponseEntity<ProblemDetail> handleApplicantVerificationRequired(
			ApplicantVerificationRequiredException exception) {
		return problem(
				HttpStatus.FORBIDDEN,
				"Verify your mobile number or email address before continuing.",
				"VERIFICATION_REQUIRED");
	}

	@ExceptionHandler(MobileVerificationRequiredException.class)
	public ResponseEntity<ProblemDetail> handleMobileVerificationRequired(
			MobileVerificationRequiredException exception) {
		return problem(
				HttpStatus.FORBIDDEN,
				"Verify your mobile number before requesting email verification.",
				"MOBILE_VERIFICATION_REQUIRED");
	}

	@ExceptionHandler(ApplicantAlreadyVerifiedException.class)
	public ResponseEntity<ProblemDetail> handleApplicantAlreadyVerified(
			ApplicantAlreadyVerifiedException exception) {
		String label = exception.channel() == ApplicantVerificationChannel.MOBILE
				? "Mobile number"
				: "Email address";
		String code = exception.channel() == ApplicantVerificationChannel.MOBILE
				? "MOBILE_ALREADY_VERIFIED"
				: "EMAIL_ALREADY_VERIFIED";
		return problem(
				HttpStatus.CONFLICT,
				label + " address is already verified.",
				code);
	}

	@ExceptionHandler(OtpChallengeNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleOtpChallengeNotFound(OtpChallengeNotFoundException exception) {
		return problem(
				HttpStatus.CONFLICT,
				"Request a new verification code before submitting one.",
				"OTP_NOT_SENT");
	}

	@ExceptionHandler(OtpExpiredException.class)
	public ResponseEntity<ProblemDetail> handleOtpExpired(OtpExpiredException exception) {
		return problem(
				HttpStatus.GONE,
				"This verification code has expired. Request a new code.",
				"OTP_EXPIRED");
	}

	@ExceptionHandler(OtpAlreadyUsedException.class)
	public ResponseEntity<ProblemDetail> handleOtpAlreadyUsed(OtpAlreadyUsedException exception) {
		return problem(
				HttpStatus.CONFLICT,
				"This verification code has already been used.",
				"OTP_ALREADY_USED");
	}

	@ExceptionHandler(InvalidOtpException.class)
	public ResponseEntity<ProblemDetail> handleInvalidOtp(InvalidOtpException exception) {
		return problem(
				HttpStatus.BAD_REQUEST,
				"The verification code is incorrect.",
				"INVALID_OTP");
	}

	@ExceptionHandler(OtpAttemptLimitExceededException.class)
	public ResponseEntity<ProblemDetail> handleOtpAttemptLimitExceeded(
			OtpAttemptLimitExceededException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Too many incorrect verification attempts. Request a new code later.");
		problem.setProperty("code", "OTP_ATTEMPTS_EXCEEDED");
		problem.setProperty("retryAfterSeconds", 60);
		return ResponseEntity.status(429).header("Retry-After", "60").body(problem);
	}

	@ExceptionHandler(OtpRateLimitException.class)
	public ResponseEntity<ProblemDetail> handleOtpRateLimit(OtpRateLimitException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.TOO_MANY_REQUESTS,
				"Wait before requesting another verification code.");
		problem.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
		problem.setProperty("code", exception.code());
		problem.setProperty("retryAfterSeconds", exception.retryAfterSeconds());
		problem.setProperty("rateLimitScope", exception.scope().name());
		if (exception.serverTime() != null) problem.setProperty("serverTime", exception.serverTime());
		if (exception.retryAt() != null) problem.setProperty("retryAvailableAt", exception.retryAt());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(exception.retryAfterSeconds())).body(problem);
	}

	@ExceptionHandler(MobileNumberChangeRateLimitException.class)
	public ResponseEntity<ProblemDetail> handleMobileNumberChangeRateLimit(
			MobileNumberChangeRateLimitException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.TOO_MANY_REQUESTS,
				"Wait before changing your mobile number again.");
		problem.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
		problem.setProperty("code", "MOBILE_CHANGE_RATE_LIMITED");
		problem.setProperty("retryAfterSeconds", exception.retryAfterSeconds());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(exception.retryAfterSeconds())).body(problem);
	}

	@ExceptionHandler(PaymentRequiredForApprovalException.class)
	public ResponseEntity<ProblemDetail> handlePaymentRequired(PaymentRequiredForApprovalException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "PAYMENT_REQUIRED_FOR_APPROVAL");
	}

	@ExceptionHandler(InvalidPaymentSignatureException.class)
	public ResponseEntity<ProblemDetail> handleInvalidPaymentSignature(InvalidPaymentSignatureException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "INVALID_PAYMENT_SIGNATURE");
	}

	@ExceptionHandler(PaymentAlreadyCompletedException.class)
	public ResponseEntity<ProblemDetail> handlePaymentAlreadyCompleted(PaymentAlreadyCompletedException exception) {
		return problem(HttpStatus.CONFLICT, exception.getMessage(), "PAYMENT_ALREADY_COMPLETED");
	}

	@ExceptionHandler(PaymentOrderNotFoundException.class)
	public ResponseEntity<ProblemDetail> handlePaymentOrderNotFound(PaymentOrderNotFoundException exception) {
		return problem(HttpStatus.NOT_FOUND, exception.getMessage(), "PAYMENT_ORDER_NOT_FOUND");
	}

	@ExceptionHandler(PaymentGatewayException.class)
	public ResponseEntity<ProblemDetail> handlePaymentGateway(PaymentGatewayException exception) {
		LOGGER.error("Payment gateway failure: {}", exception.getMessage());
		return problem(HttpStatus.BAD_GATEWAY, exception.getMessage(), "PAYMENT_GATEWAY_ERROR");
	}

	@ExceptionHandler(AdminUnauthorizedException.class)
	public ResponseEntity<ProblemDetail> handleAdminUnauthorized(AdminUnauthorizedException exception) {
		return problem(HttpStatus.UNAUTHORIZED, exception.getMessage(), "ADMIN_UNAUTHORIZED");
	}

	@ExceptionHandler(AdminLoginRateLimitException.class)
	public ResponseEntity<ProblemDetail> handleAdminRateLimit(AdminLoginRateLimitException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
		problem.setTitle("Rate Limit Exceeded");
		problem.setProperty("code", "ADMIN_RATE_LIMIT_EXCEEDED");
		problem.setProperty("retryAfterSeconds", exception.getRetryAfterSeconds());
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
				.body(problem);
	}

	@ExceptionHandler(AdminForbiddenException.class)
	public ResponseEntity<ProblemDetail> handleAdminForbidden(AdminForbiddenException exception) {
		return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "ADMIN_FORBIDDEN");
	}

	@ExceptionHandler(DocumentApprovalRequiredException.class)
	public ResponseEntity<ProblemDetail> handleDocumentApprovalRequired(DocumentApprovalRequiredException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "DOCUMENT_APPROVAL_REQUIRED");
	}

	@ExceptionHandler(ApplicationNotSubmittedException.class)
	public ResponseEntity<ProblemDetail> handleApplicationNotSubmitted(ApplicationNotSubmittedException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "APPLICATION_NOT_SUBMITTED");
	}

	@ExceptionHandler(GoogleAuthUnavailableException.class)
	public ResponseEntity<ProblemDetail> handleGoogleAuthUnavailable(GoogleAuthUnavailableException exception) {
		return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), "GOOGLE_AUTH_UNAVAILABLE");
	}

	@ExceptionHandler(GoogleIdentityConflictException.class)
	public ResponseEntity<ProblemDetail> handleGoogleIdentityConflict(GoogleIdentityConflictException exception) {
		return problem(HttpStatus.CONFLICT, exception.getMessage(), "GOOGLE_IDENTITY_CONFLICT");
	}

	@ExceptionHandler(AffiliateAccessDeniedException.class)
	public ResponseEntity<ProblemDetail> handleAffiliateAccessDenied(AffiliateAccessDeniedException exception) {
		return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "AFFILIATE_ACCESS_DENIED");
	}

	@ExceptionHandler(RestrictedSessionScopeException.class)
	public ResponseEntity<ProblemDetail> handleRestrictedSessionScope(RestrictedSessionScopeException exception) {
		return problem(HttpStatus.FORBIDDEN, exception.getMessage(), "RESTRICTED_SESSION_SCOPE");
	}

	@ExceptionHandler(InvalidAuthChallengeException.class)
	public ResponseEntity<ProblemDetail> handleInvalidAuthChallenge(InvalidAuthChallengeException exception) {
		return problem(HttpStatus.BAD_REQUEST, exception.getMessage(), "INVALID_OR_EXPIRED_OTP");
	}

	@ExceptionHandler(OnboardingSessionException.class)
	public ResponseEntity<ProblemDetail> handleOnboardingSession(OnboardingSessionException exception) {
		return problem(HttpStatus.UNAUTHORIZED, exception.getMessage(), exception.getCode());
	}

	private ResponseEntity<ProblemDetail> problem(
			HttpStatus status,
			String detail,
			String code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		return ResponseEntity.status(status).body(problem);
	}
}

