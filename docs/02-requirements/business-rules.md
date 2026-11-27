# Authoritative Business Rules

1. **Registration & Reservations**:
   - Indian mobile numbers are normalized to `+91XXXXXXXXXX`.
   - Email addresses are validated by `EmailContactPolicy` (disposable domain blocking + configurable provider allowlist) before database access.
   - Duplicate verified emails (`findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull`) and verified mobiles are rejected.
   - Unverified draft reservations expire after `app.registration.pending-reservation-ttl` (default 30 minutes) and can be reclaimed without deleting historical rows.
2. **Verification Sequence**:
   - Mobile OTP verification must succeed before email OTP delivery is permitted.
3. **Membership Activation & Admin Approval**:
   - **Current Policy**: Verified ₹599 Razorpay payment capture **immediately activates** membership and issues a permanent Affiliate ID (`ZP-YYMMDD-XXX`) without waiting for manual admin approval. (Note: Older `PRODUCT.md` text stating payment/activation are out of scope is superseded by `V10`–`V13` and `ApplicantPaymentController`.)
4. **Payment Receipts**:
   - Available only when `membership?.paymentId || membership?.orderId` is present for the authenticated owner.
   - Download filename format: `ZayloPe-Receipt-{receiptNumber}.pdf`.
