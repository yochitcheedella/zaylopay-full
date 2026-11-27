# Functional Requirements

| FR ID | BR ID | Description | Role | Status | Source References |
| --- | --- | --- | --- | --- | --- |
| **FR-01** | BR-01 | Public pages (`/`, `/about`, `/how-it-works`, `/membership`) with responsive navigation. | Public | Implemented and verified | `frontend/src/Pages/Home/Home.jsx`, `PublicLayout.jsx` |
| **FR-02** | BR-02 | Create applicant draft with `fullName`, `+91` mobile, and `EmailContactPolicy`-validated email. | Applicant | Implemented and verified | `ApplicantController.java`, `ApplicantRegistrationWriter.java` |
| **FR-03** | BR-03 | Issue and verify 6-digit mobile and email OTPs; enforce mobile-first sequence. | Applicant | Implemented and verified | `ApplicantVerificationController.java`, `VerificationService` |
| **FR-04** | BR-04 | Authenticate existing verified applicants via OTP challenge or Google ID token. | Applicant / Member | Implemented (partial test failures) | `ApplicantLoginController.java`, `GoogleAuthController.java` |
| **FR-05** | BR-04 | Manage `ZAYLOPE_ONBOARDING` and `ZAYLOPE_APPLICANT` HTTP-only cookies and logout. | Applicant / Member | Implemented and verified | `ApplicantSessionService.java`, `ApplicantSessionFilter.java` |
| **FR-06** | BR-05 | Create Razorpay order (59,900 paise) and verify HMAC signature / webhook. | Applicant | Implemented (partial test failures) | `ApplicantPaymentController.java`, `RazorpayWebhookController.java` |
| **FR-07** | BR-06 | Generate permanent `ZP-YYMMDD-XXX` Affiliate ID on captured payment. | System | Implemented (partial test failures) | `AffiliateIdService` / `ApplicantMembershipController.java` |
| **FR-08** | BR-07 | Toggle inline **View payment receipt** and **Download Receipt** (`ZayloPe-Receipt-{id}.pdf`). | Member | Implemented (browser E2E unverified) | `MembershipSuccess.jsx`, `frontend/src/utils/receiptPdf.js` |
| **FR-09** | BR-08 | Upload identity documents and store AES-256-GCM encrypted bank account numbers. | Applicant | Implemented in backend | `ApplicantDocumentController.java`, `ApplicantBankDetailsController.java` |
