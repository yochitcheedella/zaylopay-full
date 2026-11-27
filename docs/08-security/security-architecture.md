# Security Architecture

- **Session Cookies**: `ApplicantSessionFilter` validates `ZAYLOPE_APPLICANT` and `ZAYLOPE_ONBOARDING` HTTP-only cookies; `ApplicantSessionProperties` binds `secureCookie` (default `false` in dev, `true` in `application-prod.properties`).
- **OTP Security**: 6-digit OTPs stored as keyed hashes (`OTP_HASH_KEY`), rate-limited, short-lived; `dev` profile uses console mock, `prod` disables mock delivery.
- **Payment Security**: Razorpay orders are created server-side (59,900 paise); payment capture is verified via HMAC signature and webhook.
- **CORS**: Restricted to `app.cors.allowed-origin` (`http://localhost:5173` in dev).
