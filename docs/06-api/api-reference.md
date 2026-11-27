# API Reference

All APIs are served under `/api/v1/*` on port `8080` using HTTP-only cookies (`ZAYLOPE_ONBOARDING`, `ZAYLOPE_APPLICANT`) for session authentication.

| Method | Path | Controller | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/applicants/register` | `ApplicantController` | Create applicant draft & issue `ONBOARDING` cookie |
| `GET` | `/api/v1/applicants/registration-state` | `ApplicantRegistrationStateController` | Resume/inspect current registration state |
| `POST` | `/api/v1/applicants/submit` | `ApplicantSubmissionController` | Submit completed registration |
| `POST` | `/api/v1/verification/*` | `ApplicantVerificationController` | Send & verify mobile/email OTPs |
| `POST` | `/api/v1/auth/login/*` | `ApplicantLoginController` | Request & verify login OTP, logout |
| `POST` | `/api/v1/auth/google` | `GoogleAuthController` | Verify Google ID token & sign in / link |
| `GET` | `/api/v1/email-policy` | `EmailPolicyController` | Expose public email admission policy |
| `POST` | `/api/v1/payments/create-order` | `ApplicantPaymentController` | Create ₹599 Razorpay order |
| `POST` | `/api/v1/payments/verify` | `ApplicantPaymentController` | Verify Razorpay signature & activate membership |
| `GET` | `/api/v1/payments/status` | `ApplicantPaymentController` | Check payment & membership status |
| `POST` | `/api/v1/webhooks/razorpay` | `RazorpayWebhookController` | Handle Razorpay server webhooks |
| `GET` | `/api/v1/membership` | `ApplicantMembershipController` | Fetch active membership & Affiliate ID |
| `POST/GET` | `/api/v1/documents/*` | `ApplicantDocumentController` | Upload & inspect applicant documents |
| `POST/GET` | `/api/v1/bank-details/*` | `ApplicantBankDetailsController` | Save & mask encrypted bank details |
| `GET/POST` | `/api/v1/admin/*` | `AdminController` | Admin applicant review & stats |

> **Receipt Download**: Currently generated client-side in `frontend/src/utils/receiptPdf.js` (`application/pdf` Blob, `ZayloPe-Receipt-{receiptNumber}.pdf`) from verified `/api/v1/membership` / `/api/v1/payments/verify` state; no backend PDF endpoint exists.
