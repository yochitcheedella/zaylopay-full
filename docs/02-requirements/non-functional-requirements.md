# Non-Functional Requirements

| Category | Requirement | Implementation / Measured Status |
| --- | --- | --- |
| **Security** | HTTP-only session cookies, keyed-hash OTPs, Razorpay HMAC verification, AES-256-GCM bank encryption. | Implemented; `BANK_ENCRYPTION_KEY` still has a dev fallback in `BankEncryptionService.java` and `application.properties` (see [Known Issues](../12-project-status/known-issues.md)). |
| **Responsiveness** | Mobile, tablet, and desktop layouts across public and registration flows. | Implemented with CSS Grid/Flexbox and `@media` breakpoints; `axe-core` and Playwright scripts exist in `frontend/`. |
| **Accessibility** | Visible labels, `aria-live` / `role="alert"`, keyboard focus, and `MotionProvider` reduced-motion support. | Implemented across `FormControls.jsx` and `MembershipSuccess.jsx`. |
| **Data Integrity** | Pessimistic locking on registration mobile/email reservations; Flyway `V1`–`V13` migrations; `ddl-auto=none`. | Implemented in `ApplicantRegistrationWriter.java` and Flyway migrations. |
| **Maintainability** | Modular Spring Boot packages and React feature folders; `oxlint` enforced (`0` warnings/errors). | Verified via `npm run lint` (170 files). |
