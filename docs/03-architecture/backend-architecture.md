# Backend Architecture

The Spring Boot 4.1.2 (Java 21) backend under `backend/src/main/java/com/zaylope/registration/` is organized by domain module:

- `applicant/`: `Applicant` entity, `ApplicantRepository`, `BankEncryptionService` (AES-256-GCM), `ApplicantBankDetailsController`.
- `registration/`: `ApplicantController`, `ApplicantServiceImpl`, `ApplicantRegistrationWriter` (transactional with pessimistic locks), `ApplicantRegistrationLock`, `ApplicantRegistrationStateController`, `ApplicantSubmissionController`.
- `verification/` & `otp/`: `ApplicantVerificationController`, OTP generation/hashing, SMTP and dev console delivery adapters.
- `auth/`: `ApplicantLoginController`, `GoogleAuthController`.
- `session/`: `ApplicantSessionService`, `ApplicantSessionFilter`, `ApplicantSessionScope` (`ONBOARDING` vs `AUTHENTICATED`).
- `payment/`: `ApplicantPaymentController`, `RazorpayService`, `RazorpayWebhookController`.
- `affiliate/`: Affiliate ID (`ZP-YYMMDD-XXX`) generation and `ApplicantMembershipController`.
- `document/`: `ApplicantDocumentController` (multipart upload).
- `admin/`: `AdminController` (application review endpoints).
- `common/` & `config/`: `EmailContactPolicy`, `GlobalExceptionHandler`, `@ConfigurationProperties` records (`ApplicantSessionProperties`, `RegistrationProperties`, `VerificationProperties`, `RazorpayProperties`, `DocumentUploadProperties`, `EmailPolicyProperties`).
