# Business Requirements

| ID | Business Requirement | Priority | Status |
| --- | --- | --- | --- |
| **BR-01** | Provide a public web presence explaining ZayloPe, how it works, and membership benefits. | High | Implemented and verified |
| **BR-02** | Register applicants with full name, normalized Indian mobile number, and validated email address. | High | Implemented and verified |
| **BR-03** | Verify applicant mobile and email contacts via short-lived OTPs before membership activation. | High | Implemented and verified |
| **BR-04** | Support authenticated login via OTP and Google OAuth2 with HTTP-only session cookies. | High | Implemented (MySQL integration test has known failures) |
| **BR-05** | Collect ₹599 INR membership fee via Razorpay and verify payment server-side. | High | Implemented (unit-tested; MySQL integration test has known failures) |
| **BR-06** | Issue a permanent unique Affiliate ID (`ZP-YYMMDD-XXX`) upon verified payment capture. | High | Implemented (unit-tested; MySQL integration test has known failures) |
| **BR-07** | Allow authenticated members to view and download their verified payment receipt as PDF. | Medium | Implemented (lint/build/PDF-structure verified; browser E2E unverified) |
| **BR-08** | Support optional/future document upload, encrypted bank details, and admin review. | Low | Partially implemented (backend present; not in primary 3-step flow) |
| **BR-09** | Provide affiliate dashboard, referral tracking, commissions, and wallet withdrawals. | Future | Planned |
