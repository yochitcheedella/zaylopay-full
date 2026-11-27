# Project Overview

## 1. Project Name
**ZayloPe** (`zaylope-registration-backend` / `zaylope-frontend`)

## 2. Business Problem & Solution
ZayloPe provides a centralized web portal for onboarding prospective members/affiliates, verifying their identity contacts (Indian mobile numbers and email addresses), collecting a one-time membership fee, and issuing a permanent Affiliate ID (`ZP-YYMMDD-XXX`).

## 3. Target Users
- **Applicants**: Prospective members registering for the ZayloPe programme.
- **Members / Affiliates**: Authenticated users who have completed payment and received a permanent Affiliate ID.
- **Administrators**: Authorized reviewers accessing `/admin` and `/api/v1/admin/*` endpoints.

## 4. Current Application vs. Product Vision
| Capability | Status |
| --- | --- |
| Public marketing & informational pages | Implemented and verified |
| 3-step applicant registration & contact OTP verification | Implemented and verified |
| Google OAuth2 sign-in and account linking | Implemented (MySQL integration test has known failures) |
| Razorpay ₹599 membership payment & Affiliate ID issuance | Implemented (unit-tested; MySQL integration test has known failures) |
| View Payment Receipt & Download Receipt PDF | Implemented (build/lint/PDF-structure verified; browser E2E unverified) |
| Document upload & encrypted bank details | Implemented in backend; not active in primary 3-step UI |
| Affiliate dashboard, referral tracking, commissions, wallet | Planned (future roadmap) |
