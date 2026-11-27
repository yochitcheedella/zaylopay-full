# User Experience

- **Public Pages**: Clean marketing layout with `Navbar`, `Footer`, and `ScrollManager`.
- **Registration (`/register`)**: 3-step `RegistrationStepper` with inline validation, OTP countdown/retry, Razorpay checkout, and `MembershipSuccess` screen.
- **Payment Success & Receipt**:
  - **View payment receipt**: Toggles inline `.zp-ms-receipt-box` showing `Verified & Captured`, Order Reference, Payment ID, and Amount Paid.
  - **Download Receipt**: Shows `Preparing PDF...` while generating, prevents duplicate clicks, downloads `ZayloPe-Receipt-{receiptNumber}.pdf` directly, and displays an inline `role="alert"` with **Retry** on failure.
