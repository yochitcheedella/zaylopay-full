# Implementation Status

| Feature | Status | Notes |
| --- | --- | --- |
| Public Website | Implemented and verified | Prior browser checks covered named routes/viewports; lint/build alone do not prove all behavior |
| Registration & Email Admission | Implemented and verified | `EmailAdmissionBoundaryTest` (4/4), `ApplicantRegistrationWriterTest` (6/6) |
| Session Properties Binding | Implemented and verified | `@ConstructorBinding` fixed; `ApplicantSessionPropertiesTest` passes |
| OTP & Google Auth | Partially implemented | Auth integration: 4 errors; OTP integration: 3 failures, 5 errors in prior isolated run |
| Razorpay & Affiliate ID | Implemented but unverified | Membership integration: 3 errors; provider-backed end-to-end capture unverified |
| View & Download Receipt (PDF) | Implemented and verified | Synthetic browser view/download and PDFBox parsing passed; provider-backed ownership/capture remains unverified |
| Document Upload & Bank Details | Implemented but unverified | Backend retained; production storage policy pending; bank key now required externally |

This is an initial development baseline, not a production release. See [known issues](known-issues.md) for the prior 425-test full-suite result and security advisory. Current cleanup verification passed 46 selected backend regressions and 64 frontend Node tests; it did not rerun or claim to fix the full integration suite.
