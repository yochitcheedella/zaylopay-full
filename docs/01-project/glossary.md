# Glossary

| Term | Definition |
| --- | --- |
| **Applicant** | A user who has started or completed registration (`applicants` table) prior to or during membership activation. |
| **Member / Affiliate** | An applicant whose ₹599 membership payment is `PAID` and who holds an active `affiliate_id`. |
| **Affiliate ID** | Permanent unique identifier formatted as `ZP-YYMMDD-XXX` generated upon verified payment capture. |
| **OTP** | 6-digit, short-lived, keyed-hash one-time password used for mobile/email verification and login. |
| **Onboarding Session** | Short-lived HTTP-only cookie (`ZAYLOPE_ONBOARDING`, default 15m) issued during draft registration. |
| **Applicant Session** | Authenticated HTTP-only cookie (`ZAYLOPE_APPLICANT`, default 30m) issued after verified login/auth. |
| **Razorpay Order** | Server-created payment order (`order_...`) for 59,900 paise (`INR`). |
| **Captured Payment** | A Razorpay payment (`pay_...`) verified via HMAC signature or webhook. |
| **Receipt** | Verified payment summary viewable inline or downloaded as `ZayloPe-Receipt-{receiptNumber}.pdf`. |
