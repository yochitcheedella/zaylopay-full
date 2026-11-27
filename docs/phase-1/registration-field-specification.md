# Phase I Registration Field Specification

> Current implementation note: mobile and email OTP are disabled. New registrations remain unverified; verification fields remain in the schema for compatibility and historical records are preserved.

**Status:** Design baseline derived from the supplied Phase I SRS; business-specific values remain pending.  
**Sources:** The SRS states that full name, mobile number, and email are mandatory. Other exact fields/requirements are to be approved by the client.

## 1. Status vocabulary

- **Confirmed baseline:** Explicitly stated as mandatory/in scope in the supplied SRS.
- **Proposed technical validation:** Safe implementation guidance that does not decide a business policy.
- **PENDING CLIENT CONFIRMATION:** The form, requiredness, accepted value, rule, or workflow depends on a client decision. Do not turn a proposal into a production rule without sign-off.

## 2. Initial applicant registration

| Field / API name | UI label / control | Required | Data handling | Validation and errors | Decision status |
|---|---|---:|---|---|---|
| `fullName` | Full name; text input | Yes | Trim and collapse surrounding/repeated spaces; preserve Unicode spelling. | Require at least two Unicode letters overall; accept Unicode letters/combining marks, initials, spaces, apostrophes, and hyphens; reject numbers, controls, and unrelated symbols. Maximum 150 Java/JavaScript string units, matching the applicants table schema. A single-part name is accepted. Error: “Enter a valid name without numbers or unrelated symbols.” | Required is confirmed by SRS; current implementation policy. |
| `mobileNumber` | Mobile number; telephone/text input | Yes | Accept Indian national form or `+91` form; normalize and store as `+91` plus 10 digits. Do not log. | Must be 10 digits beginning 6–9, optionally prefixed by `+91`. A repeated unverified mobile reuses its pending applicant; an already verified mobile is rejected. | Required by SRS; Indian format/normalization and current duplicate behavior are confirmed. |
| `email` | Email address; email input | Yes | Trim surrounding whitespace; preserve local-part case, dots, and plus-tags; normalize only the domain to lowercase. Existing records retain legacy case-insensitive login compatibility. | Use maintained syntax/TLD validators, limit to 254 characters and 64 local-part characters, and reject known provider-domain typos until the user explicitly accepts a suggested correction. Custom domains with valid public TLDs are allowed; format does not prove mailbox existence or ownership. New identity uniqueness is case-sensitive for the local part and case-insensitive for the domain. Error: “Enter a valid email address.” | Required by SRS; new identity policy is case-sensitive local part. |
| `privacyConsent` | Privacy notice checkbox | **Conditional** | Store consent timestamp and notice version only if required by approved privacy policy. Do not store a checkbox value without evidence/version. | Must not be pre-selected. Consent text, mandatory nature, and jurisdiction are **PENDING CLIENT CONFIRMATION**. | SRS requires appropriate notice and required consent; exact text/behavior pending. |

### Duplicate and update policy

- Email is unique case-insensitively across applicants. The service checks before persistence and additive migration V6 adds a database constraint to prevent concurrent duplicates.
- A duplicate normalized mobile reuses its unverified pending applicant; a verified mobile is rejected. Registration does not send OTPs or mark either contact verified.
- If existing databases contain duplicate emails, preserve all applicant rows and have the data owner resolve conflicting addresses before applying V6. Do not delete or merge applicant records automatically.
- Avoid account enumeration: user-facing responses should not disclose the existence of another applicant unless the client approves that behavior.

### Current implementation baseline

- The registration API accepts `fullName`, `mobileNumber`, and `email`; all are required.
- `fullName` is trimmed/collapsed and limited to 150 Java/JavaScript string units, matching the existing applicants table schema and entity mapping; one-part Unicode names are accepted.
- Mobile input accepts only national ASCII digits while editing (up to 10); complete valid Indian numbers pasted/autofilled with `+91` and common separators normalize to the national digits in the input and canonical `+91` form for the API. Malformed or excessive edits are rejected, not truncated. Backend parsing independently requires 10 digits beginning 6–9.
- Email is trimmed, checked using maintained syntax/TLD validators plus the shared input policy, and limited to 254 characters (64 for the local part); the domain is lowercase-normalized and plus-tags/dots are preserved. Known domain typos show a selectable suggestion and are rejected until explicitly corrected. This check is not mailbox or ownership verification; duplicate addresses are rejected case-insensitively.
- Pending mobile duplicates reuse the existing applicant draft; verified mobile duplicates return a conflict. Registration does not send OTPs or mark either contact verified.
- **Known security gap:** the registration API returns distinct duplicate-contact outcomes before ownership is verified. The login OTP request is non-enumerating, but this registration endpoint is not. A real fix requires a pre-registration verification/challenge flow; changing only frontend wording would not prevent direct-API enumeration. This remains unresolved, and no new OTP requirement or database migration was introduced here.
- The response's `SUBMITTED` status is an acknowledgement only; no lifecycle status is persisted by this implementation.
- After successful registration, the applicant can complete MSG91 widget verification through the authenticated applicant session. Verification is separate from the registration acknowledgement; whether it gates later registration-ID issuance remains **PENDING CLIENT CONFIRMATION**.

## 3. Verification fields

| Field / API name | Required | Validation | Persistence / handling | Decision status |
|---|---:|---|---|---|
| `mobileNumber` (widget start/verify) | Yes | Accept Indian 10-digit or `+91` form; normalize and require exact match to the authenticated applicant's current number. No mobile-change endpoint exists. | Used to initialize the widget and bind backend token verification; it is not caller identity or authorization. | Indian numbering choice is selected for this implementation; future change-number workflow pending. |
| `accessToken` | Yes for backend confirmation | Required, bounded string from the MSG91 widget success callback. Backend sends it to the official Verify Access Token endpoint and requires provider success plus explicit matching mobile identity. | Transient browser/backend/provider request data only. Never log or persist it. | Provider token shape/expiry are controlled by MSG91; no local expiry is invented. |
| OTP code | Entered only in the MSG91 default widget UI | Provider-owned format, expiry, retry, and validation rules. The application does not receive or validate the code. | Not sent to the Spring API and never persisted by this application. | Configure and confirm in MSG91; policy remains provider-side. |
| `mobileVerified` | No direct applicant input | Server-managed boolean; remains false on provider error, incorrect/expired code, timeout, or mismatch. | `applicants.mobile_verified`; defaults false. | Set only after successful provider validation. |
| `mobileVerifiedAt` | No direct applicant input | Set to current UTC timestamp only after provider JSON success. | Existing `applicants.mobile_verified_at` column; null before verification. | Whether verified status gates registration-ID issuance remains pending. |
| Email verification challenge | Not implemented | Method and validation depend on approved email-provider design. | No current mobile OTP table is used. A future local email challenge requires a separate approved persistence design; no SMS OTP is stored. | Provider, method, enablement, and workflow prerequisite remain pending. |

## 4. Identity document fields

| Field / API name | Required | Validation | Persistence / handling | Decision status |
|---|---:|---|---|---|
| `documentType` | Yes for each upload | Must be in the approved allowlist. | Store as a code in `applicant_documents`. | Accepted types and whether multiple types/files are allowed are pending. |
| `file` | Yes for each upload | Server validates configured extension, content signature, MIME, and size; reject path traversal and unsafe content. | Private object storage; database stores an opaque storage key/reference, not file bytes. | Allowed formats, max file size/count, scanning/review, retention, and deletion are pending. |
| `originalFileName` | Optional metadata | Length-limit and encode on display; never trust as a path. | Store only if operationally needed; avoid in logs. | Whether retained is pending. |
| `documentStatus` | System-managed | Use approved workflow values; no automatic KYC inference. | Store in metadata. | Manual review and status vocabulary are pending. |

## 5. Bank detail fields

The SRS lists proposed fields, not approved mandatory rules. Create the bank-detail row only if the approved workflow collects these values.

| Field / API name | Required | Validation | Persistence / display | Decision status |
|---|---:|---|---|---|
| `accountHolderName` | **PENDING CLIENT CONFIRMATION** | Nonblank/length only if required; detailed name rules pending. | Store as personal data; restrict access. | Field requiredness pending. |
| `bankName` | **PENDING CLIENT CONFIRMATION** | Free-text vs approved bank list pending. | Store only if needed. | Field and input method pending. |
| `accountNumber` | **PENDING CLIENT CONFIRMATION** | Preserve leading zeroes; validate approved character/length rules. Never return/log the raw number. | Encrypt at application layer before database persistence; mask in UI. | Requiredness, jurisdiction and validation pending. |
| `confirmAccountNumber` | Conditional UI confirmation | Compare with account number in memory only. | Do not persist. | Whether confirmation field is desired is pending. |
| `ifscCode` | **PENDING CLIENT CONFIRMATION** | Apply IFSC syntax only if jurisdiction and field are confirmed. | Store as sensitive-adjacent bank metadata. | Applicability and requiredness pending. |

**Separate decision:** Capturing account details does not mean verifying the bank account. External verification is excluded by the supplied SRS unless separately approved.

## 6. Payment fields

| Field / API name | Source | Validation / trust rule | Decision status |
|---|---|---|---|
| `amount` | Server-side approved fee configuration | Use fixed-precision decimal/minor units; never trust a browser-supplied amount. | Fee/variable pricing pending. |
| `currency` | Server configuration | ISO currency code; must match gateway transaction. | Currency pending. |
| `provider` | Server configuration | Only client-selected provider and merchant account. | Provider and credentials pending. |
| `status` | Server/gateway verification | Proposed SRS states: Initiated, Pending, Successful, Failed, Cancelled, Refunded (if applicable). Webhook/status API is authoritative. | Refund applicability and retry rules pending. |
| `gatewayTransactionReference` | Gateway | Validate signature/authentication and idempotency; never accept browser redirect as proof. | Gateway pending. |

## 7. Registration and administrator fields

- `registrationId`: system-generated, nullable until agreed completion conditions are met; proposed format `REG-YYYY-XXXXXX`. Trigger, year timezone, and overflow policy require approval.
- `registrationStatus`: system-managed; the final list and permitted transitions are **PENDING CLIENT CONFIRMATION**. The finalization form suggests Draft, Submitted, Verification Pending, Payment Pending, Payment Failed, Registered, Under Review, Approved, Rejected as *proposed*, not approved.
- Administrator search: name, mobile number, or registration ID is listed in the SRS. Exact/partial search behavior, pagination limits, accessible details, and roles are pending. Results mask mobile and omit bank details by default.
- Date/time storage: store UTC timestamps; display timezone is **PENDING CLIENT CONFIRMATION**.

## 8. Common input handling

- Apply server-side validation even when React performs early feedback.
- Reject malformed JSON, unexpected fields where strict request DTOs are used, overlong values, and invalid enum codes.
- Never include OTPs, document bytes, account numbers, access tokens, or provider secrets in logs or general-purpose error bodies.
- Return stable field error codes/messages that do not disclose duplicate identities or sensitive values.

