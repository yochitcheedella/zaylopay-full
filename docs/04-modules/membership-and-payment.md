# Membership, Razorpay Payment & Affiliate ID Module

- **Overview & Purpose**: Collects the ₹599 INR (59,900 paise) membership fee via Razorpay, verifies payment server-side, activates membership, and issues a permanent `ZP-YYMMDD-XXX` Affiliate ID (`BR-05`, `BR-06`, `FR-06`, `FR-07`).
- **Frontend**: `Register.jsx`, `MembershipSuccess.jsx`, `MemberWelcomePage.jsx`.
- **Backend**: `ApplicantPaymentController.java` (`/create-order`, `/verify`, `/status`), `RazorpayWebhookController.java`, `RazorpayService.java`, `ApplicantMembershipController.java`, `affiliate/` package.
- **Database**: `applicants` (`payment_status`, `affiliate_id`), `payments` / Razorpay order records (`V10`).
- **Test Evidence**: Unit tests pass; `MembershipActivationMySqlIntegrationTest` has 3 failing cases due to email provider allowlist on test domains.
- **Status**: **Implemented** (unit-tested; MySQL integration tests require test-domain alignment).
