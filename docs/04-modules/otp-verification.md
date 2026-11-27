# OTP Verification Module

- **Overview & Purpose**: Issues and verifies 6-digit, short-lived, keyed-hash OTPs for mobile and email contacts (`BR-03`, `FR-03`).
- **Backend**: `ApplicantVerificationController.java`, `verification/` & `otp/` packages, `VerificationProperties.java`.
- **Database**: `applicant_verification_codes`.
- **Business Rules**: Mobile verification must succeed before email OTP delivery; dev profile uses console-only mock (`app.verification.mock-delivery=true`), whereas `prod` disables mock delivery.
- **Status**: **Implemented and verified** (unit tests pass; production SMS provider not configured).
