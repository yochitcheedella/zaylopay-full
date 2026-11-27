# Phase I Registration UI Wireframes

> Current implementation note: the mobile OTP screens described below are historical design only and are not present in the active UI. Registration currently proceeds without OTP and leaves verification pending.

**Status:** Technical design baseline updated to reflect the implemented applicant registration and MSG91 mobile OTP slice. Remaining business-dependent behavior is marked **PENDING CLIENT CONFIRMATION**. The project lead approved the React/Vite, Java 21/Spring Boot, and MySQL 8 stack.
**Scope:** The applicant registration and inline mobile-OTP screens below are implemented in React. Email verification, document upload, bank details, payment, confirmation, and administrator screens remain design proposals.

## 1. Design system and shared behavior

### Visual direction

- Professional, calm, high-trust experience suitable for handling identity and financial information.
- Current Service Intake Sheet palette: ink `#182F43`, muted text `#46585E`, green-gray ground `#EAEDE7`, white surface, quiet rule `#C4CDC5`, strong rule `#87948F`, terracotta action `#B55A3C`, and restrained semantic success/error colors. Do not add a competing accent.
- Typography: Segoe UI with Arial/sans-serif fallback; use the existing heading/body/label hierarchy.
- Content width: approximately 40–48rem for applicant forms; dashboard uses the available desktop width.
- Use a single-column form on mobile. On wider screens, keep each field comfortably readable and avoid forcing long sensitive fields into narrow columns.
- Meet WCAG 2.2 AA contrast and keyboard/focus expectations; do not communicate state by color alone.

### Shared applicant shell

```text
+----------------------------------------------------------------+
| ZayloPe                                  Help / Privacy notice  |
+----------------------------------------------------------------+
| 1 Personal details -- 2 Verify -- 3 Documents -- 4 Bank -- ... |
|                                                                |
|                 [Current step content]                        |
|                                                                |
| [Back]                                        [Continue]       |
+----------------------------------------------------------------+
| Secure registration | Support contact: PENDING CLIENT CONFIRMATION |
+----------------------------------------------------------------+
```

- The stepper reflects only stages enabled by the client-approved workflow. Do not imply verification or payment is required before that decision is made.
- On small screens, replace the horizontal stepper with “Step N of M” and the current step name.
- Keep submitted form values when navigating backward. Do not put OTPs, bank account numbers, document contents, or tokens in browser storage, URLs, analytics, or console logs.
- Each control has a persistent visible label, programmatic label, required indication, inline error text, and a visible focus state. Associate errors with controls (`aria-describedby`) and announce submission results accessibly.
- On failed submission, preserve safe values, focus the error summary, and show field-specific messages. Never echo sensitive field values in an error.
- Loading state disables duplicate submissions and explains the pending action. Success state is explicit and announced.

## 2. Applicant Registration Page

**Purpose:** Capture the initial applicant identity/contact details.

```text
Applicant registration
Enter your personal and contact details to submit your registration.

Full name *       [________________________________]
Mobile number *   [________________________________]
                  Use an Indian number: 10 digits or +91 followed by 10 digits.
Email address *   [________________________________]
[Submit registration]
All fields are required.
```

| Label | Input | Mandatory | Validation / error |
|---|---|---:|---|
| Full name | Text | Yes (SRS baseline) | Trim whitespace; reject empty input and values outside the configured length. Allowed character rules are **PENDING CLIENT CONFIRMATION**. “Enter your full name.” |
| Mobile number | Telephone input | Yes (SRS baseline) | Accept Indian 10 digits beginning 6–9 or `+91` followed by those digits, then normalize to `+91`. “Enter a valid Indian mobile number using 10 digits or +91 followed by 10 digits.” |
| Email address | Email | Yes (SRS baseline) | Validate syntax and maximum length; normalize according to approved comparison rules. “Enter a valid email address.” |
| Privacy notice consent | Checkbox | Required to submit if consent is required by approved policy | Consent wording/version and whether explicit consent is required: **PENDING CLIENT CONFIRMATION**. Do not pre-check. “Please review and accept the privacy notice to continue.” |

**Actions and behavior**

- `Submit registration`: client-side format checks followed by `POST /api/v1/applicants/register` with `fullName`, `mobileNumber`, and `email`. Show field errors, retryable service error, or loading/success state. The HTTP `201` response contains `message` and acknowledgement `status`; the backend also issues the applicant-scoped HttpOnly cookie. The response omits internal identifiers.
- Do not reveal whether a specific mobile number or email is already registered unless the approved duplicate policy permits it; use a privacy-safe response.
- Duplicate identity rules, contact edit policy, supported alphabets, privacy-consent text, and recovery flow: **PENDING CLIENT CONFIRMATION**. The registration page does not currently collect a consent checkbox.

**Implementation note:** Registration validates Indian number format and normalizes to `+91`; duplicate contact values are currently accepted. The response's `SUBMITTED` value confirms that the applicant row was saved; it is not a persisted lifecycle status or a final registration-ID confirmation.

## 3. Mobile OTP Verification (MSG91 widget, implemented inline after registration)

**Purpose:** Verify control of the applicant's submitted Indian mobile number through MSG91's default OTP Widget UI. The OTP is generated, delivered, and verified inside MSG91; the application does not implement its own OTP lifecycle or custom OTP-entry form.

```text
Verify your mobile number
Your registration details have been received. Verify your mobile number to complete registration.

Registration                 Received
Mobile verification          Pending
Account activation           Pending

Verify [masked number] using MSG91's secure verification window.
[Verify mobile number]

MSG91 default widget UI: enter mobile / OTP / provider flow
Accessible status / safe provider error
Continue in the MSG91 verification window to receive and verify your code.

Verified state: Registration complete; mobile verified; account activation pending
```

| Label | Input | Mandatory | Validation / error |
|---|---|---:|---|
| Mobile number | Read-only masked value from the submitted registration | Yes; no edit action in this flow | OTP endpoint requires the authenticated applicant's current mobile number. “Use the mobile number submitted with this registration.” |
| MSG91 widget fields | Provider-owned default UI, initialized with the applicant's `91`-prefixed Indian number | Required while mobile verification is enabled | The widget owns OTP input and provider-side validation. The app does not receive the code. Widget failures are shown as safe provider messages; final verified state is based only on the backend response. |

**Actions and behavior**

- `Verify mobile number`: load the MSG91 SDK once, then call authenticated `POST /api/v1/otp/widget/start` to validate the applicant and reserve a widget-start cooldown. Initialize the default UI with the applicant's registered number; no direct MSG91 send API is called.
- Widget success callback: send its access token with the registered mobile number to authenticated, CSRF-protected `POST /api/v1/otp/verify`. The backend validates the token using the server Auth Key and checks the provider-returned identity matches the applicant before saving verification.
- Keep registration, mobile verification, and account activation statuses separate. Show registration as received and verification/activation as pending until the backend confirms verification; then show registration complete and mobile verified while account activation remains pending.
- Show loading, safe provider failure, rejected/expired code, cancellation, invalid token, mismatch, rate-limit, authentication, authorization, server, and network error states. The server marks the number verified only after its provider check succeeds.
- The frontend registers MSG91 `success` and `failure` callbacks. No separate cancellation callback is wired; only classify a cancellation when the failure callback identifies one.
- Reopening the widget is subject to the 30-second application cooldown. Resends and OTP-entry attempts inside the default widget are provider-managed and require MSG91-side limits.
- A mobile-number change flow is intentionally absent. The submitted number must match the authenticated applicant's current number.
- The frontend calls the MSG91 Widget SDK directly for its managed UI, but never calls the direct MSG91 OTP API. OTPs and access tokens are not placed in URLs, logs, analytics, or persistent browser storage.
- Server limits apply to widget starts and token-validation requests; the provider controls internal widget resends and code-entry attempts. The server limiter is in-memory and JVM-local.

## 4. Email Verification Screen

**Purpose:** Verify access to the submitted email address, only when enabled.

```text
Verify your email
Instructions sent to [masked email]

Enter the code *     [________________]
                      [Verify email]

Didn't receive it? [Resend instructions]
```

| Label | Input | Mandatory | Validation / error |
|---|---|---:|---|
| Verification code or link token | Code input for OTP flow; link landing state for link flow | Yes when email verification is enabled | Delivery/verification method (OTP or time-limited link), length, expiry, retry policy, and provider are **PENDING CLIENT CONFIRMATION**. “The verification information is invalid or expired.” |

**Actions and behavior**

- `Verify email`: submit the code/token to `POST /api/v1/applicants/me/verifications/email`.
- `Resend instructions`: request a fresh challenge and show a non-sensitive delivery status.
- Link-based verification must validate the token server-side and must not expose applicant details in the URL.
- Continue only when the approved workflow requires this verification and it succeeds.

## 5. Identity Document Upload Screen

**Purpose:** Collect the approved identity-proof documents and show their submission state.

```text
Identity documents
Upload the document types requested by the organization.

Document type *     [Select an approved document type v]
Choose file *       [Choose file]  No file selected
                    Accepted formats / size: PENDING CLIENT CONFIRMATION
[Upload document]

Uploaded documents
Type                 File name (safe display)     Status       [Remove]
...

[Back]                                         [Continue]
```

| Label | Input | Mandatory | Validation / error |
|---|---|---:|---|
| Document type | Select | Yes for each uploaded document | Options come only from the client-approved allowlist. |
| Choose file | File picker / drag-and-drop enhancement | Yes for each uploaded document | Format, maximum size, count, and whether multiple documents are allowed are **PENDING CLIENT CONFIRMATION**. Validate extension, declared MIME type, and file signature server-side. “This file type is not supported.” / “The file exceeds the allowed size.” |

**Actions and behavior**

- `Upload document`: multipart upload to `POST /api/v1/applicants/me/documents`; show progress and per-file success/failure.
- `Remove`: delete/mark the uploaded reference according to retention rules; deletion semantics are **PENDING CLIENT CONFIRMATION**.
- Files are stored in private object storage, never rendered inline from an untrusted public URL, and never stored as database BLOBs by default.
- Scan/quarantine policy, document review requirement, accepted types, retention, and whether a pending review blocks registration: **PENDING CLIENT CONFIRMATION**.

## 6. Bank Details Screen

**Purpose:** Collect only the approved bank information; no bank-account verification is implied.

```text
Bank details
These details are [purpose: PENDING CLIENT CONFIRMATION].

Account holder name *    [____________________________]
Bank name *              [____________________________]
Account number *         [____________________________]
Confirm account number   [____________________________]
IFSC code *              [____________]

Bank details are encrypted and shown masked after saving.
[Back]                                         [Save and continue]
```

| Label | Input | Mandatory | Validation / error |
|---|---|---:|---|
| Account holder name | Text | **PENDING CLIENT CONFIRMATION** | Required/format policy and maximum length to be approved. |
| Bank name | Text or approved selector | **PENDING CLIENT CONFIRMATION** | Whether free-text or controlled list is used is **PENDING CLIENT CONFIRMATION**. |
| Account number | Password-style numeric/text field | **PENDING CLIENT CONFIRMATION** | Preserve leading zeros; never log or return the full value. Length/character rules are **PENDING CLIENT CONFIRMATION**. |
| Confirm account number | Password-style numeric/text field | **PENDING CLIENT CONFIRMATION** | UI-only equality check; do not persist the confirmation value. |
| IFSC code | Text | **PENDING CLIENT CONFIRMATION** | IFSC format is only applicable if the client confirms the banking jurisdiction and required field. |

**Actions and behavior**

- `Save and continue`: submit to `PUT /api/v1/applicants/me/bank-details` over HTTPS; the backend encrypts sensitive data before persistence.
- Mask the account number after save (for example, display only the last few digits); exact masking is a technical security recommendation.
- Account verification, bank update policy, optionality, and the authorized roles permitted to reveal bank details are **PENDING CLIENT CONFIRMATION**.

## 7. Payment Screen

**Purpose:** Initiate an approved registration fee using the selected gateway, only if payment is applicable.

```text
Registration payment
Amount: [server-provided amount and currency]
Payment provider: [client-selected provider]

You will complete payment on the provider's secure checkout.

[Continue to secure payment]
Status: [Initializing / Pending / Successful / Failed / Cancelled]
[Retry payment]  (only when server says retry is allowed)
```

**Fields:** No card, UPI PIN, or bank login fields are collected by this application. Amount/currency are read-only server-provided values, never trusted from the browser.

**Validation / errors**

- The server checks whether payment is required, the configured fee/currency, applicant eligibility, and idempotency. These business values are **PENDING CLIENT CONFIRMATION**.
- Show “Payment could not be started. Please try again.” for a retryable initiation error; “Payment status is being confirmed.” while pending; and a clear failed/cancelled result without treating browser redirects as authoritative.

**Actions and behavior**

- `Continue to secure payment`: `POST /api/v1/applicants/me/payment-sessions`; redirect/open the provider checkout URL returned by the server.
- Verify outcomes through a signed server-to-server webhook or server-side gateway verification. Do not generate the registration ID from a browser success redirect alone.
- Retry and refund behavior are **PENDING CLIENT CONFIRMATION**.

## 8. Registration Success Screen

**Purpose:** Confirm the final state and show the registration ID only when it has been issued.

```text
Registration status
[Success / Submitted / Payment pending / Under review]

Applicant: [name]
Registration ID: [REG-YYYY-XXXXXX or “Not yet issued”]
Submitted: [date/time and display timezone]
Payment: [status, only if applicable]

[Print / Download confirmation]   [Return to sign-in/start]
```

**Behavior**

- Read from `GET /api/v1/applicants/me/confirmation`; never infer success from the client’s last action.
- If completion conditions are not met, display the current approved status and next action instead of presenting a final registration ID.
- Whether confirmation can be emailed/SMSed, receipt contents, date timezone, and print/download support are **PENDING CLIENT CONFIRMATION**.

## 9. Basic Administrator Dashboard

**Purpose:** Let authorized administrators find records, see approved status/payment information, and access documents only when authorized.

```text
Administrator dashboard
Search [name, mobile, or registration ID] [Search]
Filters: Registration status [v]   Payment status [v]

Applicants
Registration ID | Applicant | Mobile (masked) | Status | Payment | Submitted
...
[Previous] Page N [Next]

Selected record: [View details] [Review documents if authorized] [Update status if permitted]
```

**Controls and behavior**

- Search text: one input; trim and length-limit it server-side. Searchable fields are from the SRS; exact matching/partial matching is **PENDING CLIENT CONFIRMATION**.
- Filters: registration/payment status values are client-approved configuration, not hard-coded assumptions.
- Results are paginated and return only fields needed for operations. Mask mobile and never include full bank account numbers in list results.
- Applicant detail view has separate authorization checks for identity documents and bank data. Any bank reveal is denied by default until an explicit role/need is approved.
- Status update requires an authorized role and a permitted transition; transitions, reason requirements, and admin roles are **PENDING CLIENT CONFIRMATION**.
- Show loading, empty, access-denied, and retryable server-error states. Do not reveal whether an applicant exists to an unauthorized user.

## 10. Accessibility and responsive acceptance checklist

- All actions are keyboard accessible; focus is visible and follows the visual order.
- Form labels remain visible; errors are textual, programmatically associated, and announced.
- OTP supports paste and accessible per-field or single-field entry.
- Layout works at 320 CSS pixels without horizontal scrolling for core forms.
- Touch targets and contrast meet WCAG 2.2 AA; loading/disabled states remain understandable.
- Sensitive values are masked where appropriate and never copied into telemetry.

