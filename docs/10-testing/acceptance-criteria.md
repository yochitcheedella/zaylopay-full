# Acceptance Criteria & Traceability Matrix

| BR | FR | Module | Source | API / DB | Test Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- |
| BR-01 | FR-01 | Public Website | `Home.jsx`, `PublicLayout.jsx` | — | `npm run lint`, `npm run build` | Verified |
| BR-02 | FR-02 | Registration | `ApplicantRegistrationWriter.java` | `POST /api/v1/applicants/register`, `applicants` | `EmailAdmissionBoundaryTest` (4/4), `ApplicantRegistrationWriterTest` (6/6) | Verified |
| BR-03 | FR-03 | OTP Verification | `ApplicantVerificationController.java` | `/api/v1/verification/*`, `applicant_verification_codes` | Unit tests pass | Verified |
| BR-04 | FR-04, FR-05 | Auth & Sessions | `ApplicantLoginController.java`, `ApplicantSessionProperties.java` | `/api/v1/auth/*`, `applicant_sessions` | `ApplicantSessionPropertiesTest` passes; `AuthMySqlIntegrationTest` has 4 errors | Partial |
| BR-05, BR-06 | FR-06, FR-07 | Payment & Affiliate ID | `ApplicantPaymentController.java` | `/api/v1/payments/*`, `payments` | Unit tests pass; `MembershipActivationMySqlIntegrationTest` has 3 errors | Partial |
| BR-07 | FR-08 | Payment Receipt | `MembershipSuccess.jsx`, `receiptPdf.js` | Client-side PDF 1.4 from verified state | `npm run lint`, `npm run build`, Node PDF check | Implemented (E2E unverified) |
