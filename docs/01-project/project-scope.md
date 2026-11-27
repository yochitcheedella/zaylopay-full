# Project Scope

## Current Development Scope (Implemented)
- Public SPA pages (`/`, `/about`, `/how-it-works`, `/membership`).
- Applicant registration (`/register`) with full name, normalized Indian mobile (`+91XXXXXXXXXX`), and email admission policy validation.
- Mobile and Email OTP verification (`/api/v1/verification/*`).
- Login via OTP (`/login`) and Google OAuth2 (`/api/v1/auth/google`).
- HTTP-only cookie session management (`ZAYLOPE_APPLICANT`, `ZAYLOPE_ONBOARDING`).
- Razorpay order creation, payment verification, webhook processing, and Affiliate ID generation.
- Member welcome/overview page (`/welcome`, `/dashboard`) and payment receipt view/PDF download.
- Backend document upload, encrypted bank details, and admin endpoints.

## Excluded / Future Scope
- Live Zaylo eCommerce product catalog and referral link attribution tracking.
- Commission calculation engine, member wallet ledger, and bank withdrawal processing.
- Production SMS gateway integration (currently console-only mock in `dev`).
