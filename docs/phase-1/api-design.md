# Phase I Spring Boot REST API Design

**Base path:** `/api/v1`  
**Format:** JSON over HTTPS, except multipart uploads and the payment-provider webhook.  
**Current runtime status:** Registration issues an applicant-scoped HttpOnly cookie. Applicant contact verification and login OTP endpoints are implemented; delivery depends on the configured provider and hash secret. Google authentication and account linking are also implemented. Spring Security/CSRF production hardening, administrator authentication, and payment-gateway signature verification remain future requirements.

**Implemented baseline:** `POST /api/v1/applicants/register` accepts `fullName`, `mobileNumber`, and `email`, saves an unverified draft, issues the applicant cookie, and returns HTTP `201` with `message` and `status`. The public response omits internal identifiers. Mobile inputs must be valid Indian numbers and are normalized to `+91` form. Repeated registration reuses an unverified draft; a verified number is rejected. Mobile verification is not performed during registration.

## 1. API conventions

- Use DTOs; never serialize JPA entities directly.
- Use plural resource names, stable IDs, UTC ISO-8601 timestamps, and JSON `camelCase`.
- Validation is performed server-side for every field. Do not trust browser-supplied amounts, status, applicant IDs, provider status, or file metadata.
- Registration is public and does not require an applicant cookie. Applicant-scoped routes derive ownership from the active applicant session cookie; internal numeric DB IDs are not accepted as authorization. The current session filter is temporary and is not a production security configuration.
- Registration issues a 32-byte random opaque capability in the `ZAYLOPE_APPLICANT` HttpOnly, SameSite=Strict cookie, scoped to `/api/v1` and currently valid for 30 minutes. Only its SHA-256 hash is stored. Secure-cookie mode is controlled by `APPLICANT_SESSION_SECURE_COOKIE` and must be enabled in production. Renewal and recovery remain pending.
- Admin endpoints are not active until staff authentication is configured. Roles/identity provider/account provisioning are **PENDING CLIENT CONFIRMATION**.
- Optional modules not implemented (email verification, bank capture, payment) must not be silently treated as completed.
- Shared error format follows Spring `ProblemDetail` style; include `code`, `title`, `status`, `detail`, `instance`, optional `traceId`, and field-level `errors`. Never echo OTP, bank account, document content, tokens, or provider secrets.

### Common error example

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/applicants/register",
  "code": "VALIDATION_FAILED",
  "traceId": "server-generated-correlation-id",
  "errors": [
    { "field": "email", "code": "INVALID", "message": "Enter a valid email address." }
  ]
}
```

Common status codes: `400` malformed/invalid request; `401` missing/invalid authentication; `403` insufficient role; `404` resource absent or intentionally concealed; `409` state/idempotency/duplicate conflict; `413` configured upload limit exceeded; `415` unsupported media type; `422` valid syntax but invalid workflow state; `429` rate limit; `500` unexpected server failure; `502` gateway failure. Error details must not disclose whether a contact belongs to another applicant unless approved.

### Login with a contact code

#### `POST /auth/login/otp/request`

Public request body: `{ "identifier": "9876543210" }` or `{ "identifier": "person@custom-domain.dev" }`. Mobile identifiers are normalized to canonical `+91` format. Email normalization preserves local-part case and lowercases only the domain. Exact identity matching takes precedence; a case-insensitive fallback is limited to pre-migration legacy records for login recovery.

The backend looks up only verified mobile/email identities or an eligible unverified email draft recovery. Restricted accounts and ineligible contacts do not receive a challenge. Rate limits run before the account lookup for every well-formed identifier. A challenge is saved only for an eligible target; OTP delivery starts asynchronously after the challenge transaction commits. Codes are never returned in JSON or ordinary logs.

Eligible, nonexistent, unverified, restricted, and unavailable-delivery cases use HTTP `200` with the same generic response shape and message. A nonexistent or ineligible identifier receives a random opaque reference with no corresponding challenge. This avoids revealing account existence through response content/status or synchronous provider latency. Delivery-provider failure leaves the eligible challenge unusable and still returns the generic response; the user can request another code after the cooldown.

```json
{
  "accepted": true,
  "challengeId": "opaque-reference",
  "resendAfterSeconds": 60,
  "expiresInSeconds": 300,
  "serverTime": "2026-11-26T14:46:00Z",
  "expiresAt": "2026-11-26T14:51:00Z",
  "resendAvailableAt": "2026-11-26T14:47:00Z",
  "message": "If your details match an eligible account, you'll receive a 6-digit verification code."
}
```

The response intentionally has no recipient or account-state field. The browser masks the identifier it already has locally; it does not receive additional identity information from the server.

#### `POST /auth/login/otp/verify`

Request: `{ "challengeId": "opaque-reference", "identifier": "9876543210", "code": "012345" }`. Verification requires a challenge bound to the applicant, normalized destination, channel, purpose, and issuance. A code is six digits, short-lived, attempt-limited, and consumed once. Only successful backend verification creates/rotates an applicant session and returns the server-resolved eligible registration step. Unknown references and incorrect, expired, replayed, or cross-account codes return the same generic invalid/expired response.

Responses: `200` authenticated; `400 INVALID_OR_EXPIRED_OTP`; `429` abuse limit or attempt limit; `503` database/authentication service unavailable. The frontend countdown uses server timestamps for display only; backend expiration remains authoritative.

## 2. Applicant registration

### `POST /applicants/register`

**Auth:** Public. Creates the applicant and an applicant-scoped session capability in a single transaction. Successful responses set the HttpOnly `ZAYLOPE_APPLICANT` cookie; the JSON body does not include an applicant ID or token. Registration rate limiting is not yet implemented and is required before production.

Request:

```json
{
  "fullName": "Example Applicant",
  "mobileNumber": "9876543210",
  "email": "applicant@example.invalid"
}
```

The current request accepts only `fullName`, `mobileNumber`, and `email`. Mobile number must be a valid Indian 10-digit number beginning 6–9, either as national digits or `+91` plus those digits; the server stores `+91` form. Privacy-consent capture is not part of this endpoint; consent wording and whether explicit consent is required remain **PENDING CLIENT CONFIRMATION**.

Response `201 Created`:

```json
{
  "message": "Registration draft created successfully",
  "status": "DRAFT"
}
```

No applicant ID, registration ID, or access token is returned in JSON. Applicant authentication is provided by the response cookie for the applicant's protected registration workflow; lifecycle vocabulary and the final registration-ID trigger are **PENDING CLIENT CONFIRMATION**.

**Validation:** `fullName`, `mobileNumber`, and `email` are required. The implementation trims surrounding whitespace, limits name/mobile/email lengths to 150/32/254 characters, validates email syntax, and validates/normalizes Indian mobile numbers. It does not verify number reachability. Email duplicate identity preserves local-part case and normalizes the domain to lowercase. A repeated pending mobile reuses its applicant draft; an already verified mobile is rejected.  
**Current responses:** `201` saved and cookie issued; `400` malformed or invalid request; `409 EMAIL_ALREADY_REGISTERED` or `MOBILE_ALREADY_VERIFIED`; `503` database unavailable. Rate limiting (`429`) remains a security requirement before production.

## 3. Historical MSG91 widget proposal (not active)

The widget endpoints and flows described below are retained for design history only. They are not the current contact-verification implementation; current routes are under `/applicants/me/verification/{mobile,email}` as documented in `docs/otp-verification-day6.md`. Spring Security and its CSRF bootstrap are temporarily disabled; the historical `/csrf` design below is not active.

### `GET /csrf`

**Auth:** Applicant cookie. Bootstraps the CSRF token for cookie-authenticated mutations.

Response `200 OK`: `{ "token": "<csrf-token>" }`; a CSRF cookie is also set. The client sends the returned token in the `X-XSRF-TOKEN` header on OTP POST requests. Missing/invalid applicant session: `401`.

### `POST /otp/widget/start`

**Auth:** Applicant (`ZAYLOPE_APPLICANT` cookie), with `X-XSRF-TOKEN`. Ownership is taken from the authenticated principal; the request must not contain `applicantId`.

Request:

```json
{ "mobileNumber": "9876543210" }
```

Accepts an Indian 10-digit national form or `+91` form. The backend normalizes the number and requires it to match the applicant's current registered number. No mobile-change endpoint exists. The backend applies a widget-start cooldown/rate limit and does not call MSG91 or send an OTP itself.

Response `202 Accepted`: `{ "message": "Continue with the MSG91 verification window to verify your mobile number.", "status": "WIDGET_READY" }`.

Errors: `400` invalid number or mismatch; `401` no valid applicant cookie; `403` CSRF/access denied; `404` applicant missing; `409` already verified; `429` cooldown/rate limit. The cooldown is consumed when preflight succeeds, even if the SDK subsequently fails to initialize.

### `POST /otp/verify`

**Auth:** Applicant cookie and `X-XSRF-TOKEN`.

Request:

```json
{ "mobileNumber": "+919876543210", "accessToken": "<MSG91 widget access token>" }
```

The number is normalized and must match the authenticated applicant's current number. The backend sends the transient access token to `POST https://control.msg91.com/api/v5/widget/verifyAccessToken` in a JSON `access-token` property, using the backend-only `authkey` header. HTTP success alone is insufficient: only provider JSON `type=success` plus an explicit returned mobile identity matching the applicant's current number is accepted. The provider may return the identity as `mobile`, `mobileNumber`, `mobile_number`, `identifier`, `phone`, or an exact phone-number `message` value, at the response root or under `data`. A generic success message or missing identity is rejected. MSG91 owns OTP generation and validation; the application does not receive, store, or validate the OTP.

Response `200 OK`:

```json
{
  "message": "Mobile number verified successfully",
  "status": "VERIFIED",
  "verified": true,
  "verifiedAt": "2026-11-17T16:30:00Z"
}
```

Only after provider token success and exact identity match does the backend transactionally set `mobile_verified=true` and `mobile_verified_at`. A repeated request for an already verified applicant returns the existing verified state. Invalid/rejected token or a successful response without identity: `422 INVALID_WIDGET_TOKEN`; mismatched identity/mobile: `400 MOBILE_NUMBER_MISMATCH`; unauthenticated: `401`; CSRF/access denied: `403`; missing applicant: `404`; throttled: `429`; provider failure/timeout: `503`. Failed verification does not alter verification state.

**Current throttles:** 30-second widget-start cooldown; 5 widget starts per number and per applicant per hour; 20 widget starts per IP per hour; 5 backend token-validation requests per number and applicant within the configured five-minute window; and 20 token-validation requests per IP per hour. The limiter is in-memory and JVM-local and must be replaced with a shared limiter before horizontal scaling. The default widget owns internal resend and invalid-code attempt behavior; configure provider-side limits because the application does not observe every widget operation.

### `POST /applicants/me/verification-challenges/email`

**Auth:** Applicant. **Purpose:** Send email OTP or verification-link instructions when enabled.

Request:

```json
{ "method": "OTP" }
```

`method` may instead be `LINK` if the client selects that approach.  
Response `202 Accepted`: `{ "challengeId": "opaque-challenge-reference", "destinationMasked": "a••••••t@example.invalid", "expiresAt": "2026-01-01T12:05:00Z" }`

Method, TTL, provider, retry/attempt limits, and whether enabled are **PENDING CLIENT CONFIRMATION**.

### `POST /applicants/me/verifications/email`

**Auth:** Applicant. **Purpose:** Complete email verification using code or link token.

Request (OTP): `{ "challengeId": "opaque-challenge-reference", "code": "000000" }`  
Request (link): `{ "verificationToken": "<opaque-token>" }`  
Response `200 OK`: `{ "verified": true, "verifiedAt": "2026-01-01T12:03:00Z" }`

Tokens must be one-time, time-limited, and excluded from logs/referrers. Invalid/expired challenge responses match the mobile endpoint.

## 4. Identity document upload

### `POST /applicants/me/documents`

**Auth:** Applicant. **Content-Type:** `multipart/form-data`.  
Parts: `metadata` JSON (`documentType`) and `file` binary. Document types, accepted formats, size/count limits, scanning, and review policy are **PENDING CLIENT CONFIRMATION**.

Response `201 Created`:

```json
{
  "documentId": "opaque-document-reference",
  "documentType": "CLIENT_APPROVED_CODE",
  "status": "RECEIVED",
  "uploadedAt": "2026-01-01T12:03:00Z"
}
```

Validate allowlisted type, actual file signature/MIME, file size, filename, and upload state server-side; store bytes in private object storage and only reference metadata in MySQL. Do not return storage keys or permanent public URLs. `400`, `401`, `403`, `413`, `415`, `422`, `429`, `503`.

### `GET /applicants/me/documents`

**Auth:** Applicant. Returns only the caller's document metadata.

Response `200 OK`:

```json
{
  "items": [
    { "documentId": "opaque-document-reference", "documentType": "CLIENT_APPROVED_CODE", "status": "RECEIVED", "uploadedAt": "2026-01-01T12:03:00Z" }
  ]
}
```

Delete/replace behavior is **PENDING CLIENT CONFIRMATION**; do not add a delete endpoint until retention and review effects are approved.

## 5. Bank details

### `PUT /applicants/me/bank-details`

**Auth:** Applicant. **Purpose:** Create/replace the current details when collection is approved.

Request (illustrative proposed fields):

```json
{
  "accountHolderName": "Example Applicant",
  "bankName": "Example Bank",
  "accountNumber": "000012345678",
  "confirmAccountNumber": "000012345678",
  "ifscCode": "ABCD0001234"
}
```

Field selection/requiredness are pending. `confirmAccountNumber` is compared in memory and never persisted. The API encrypts the account number before storing it.

Response `200 OK`:

```json
{
  "bankDetails": {
    "accountHolderName": "Example Applicant",
    "bankName": "Example Bank",
    "accountNumberMasked": "••••••5678",
    "ifscCode": "ABCD•••••••"
  },
  "updatedAt": "2026-01-01T12:03:00Z"
}
```

`400`, `401`, `403`, `404`, `422` (field invalid or collection disabled), `429`, `500`. Full account number is never returned, even to the applicant by default.

### `GET /applicants/me/bank-details`

**Auth:** Applicant. Returns masked values only; `404` if no record or collection is disabled. Applicant bank edit/retrieval policy is **PENDING CLIENT CONFIRMATION**.

## 6. Payment

### `POST /applicants/me/payment-sessions`

**Auth:** Applicant. **Purpose:** Initiate a payment using server-configured amount/provider.

Request:

```json
{ "idempotencyKey": "client-generated-unique-request-key" }
```

Response `201 Created`:

```json
{
  "paymentId": "opaque-payment-reference",
  "status": "INITIATED",
  "amount": "0.00",
  "currency": "XXX",
  "checkoutUrl": "https://gateway.example.invalid/checkout/opaque-reference"
}
```

Amounts/currency are placeholders only; fee, currency, gateway, retry and payment requirement are **PENDING CLIENT CONFIRMATION**. Create/replay using idempotency; never accept client-supplied fee or treat redirect as proof. `400`, `401`, `403`, `409`, `422`, `429`, `502`, `503`.

### `GET /applicants/me/payments/{paymentId}`

**Auth:** Applicant. **Purpose:** Return payment status after server-side gateway verification/reconciliation.

Response `200 OK`:

```json
{
  "paymentId": "opaque-payment-reference",
  "status": "PENDING",
  "amount": "0.00",
  "currency": "XXX",
  "updatedAt": "2026-01-01T12:03:00Z"
}
```

`404` for another applicant's/unknown payment; `401`, `403`, `502` if upstream verification is unavailable. Do not convert an unavailable gateway response into a success/failure assumption.

### `POST /webhooks/payments/{providerCode}`

**Auth:** Public route with gateway signature verification, timestamp/replay checks, and provider configuration. Not browser-callable as a trusted payment result.

Request body: provider-defined raw payload; verify signature against raw bytes before parsing/trusting.  
Response `200 OK`: `{ "received": true }` after durable/idempotent processing; invalid signature `401`; malformed/unsupported event `400`; transient persistence failure `5xx` so the gateway can retry according to its contract.

Deduplicate provider event/transaction references; apply legal payment state transitions once; never log secrets/full payloads.

## 7. Registration ID and confirmation

### `GET /applicants/me/registration`

**Auth:** Applicant. Returns current registration status and an ID only if already issued.

Response `200 OK`:

```json
{
  "registrationId": null,
  "status": "PENDING",
  "submittedAt": "2026-01-01T12:00:00Z",
  "paymentStatus": "NOT_APPLICABLE",
  "nextAction": "PENDING_CLIENT_CONFIRMATION"
}
```

Status/next-action values are illustrative; final lifecycle values and ID trigger are pending.

### `POST /applicants/me/registration-id`

**Auth:** Applicant. **Purpose:** Idempotently request final ID issuance after server-side evaluation of the approved completion conditions.

Request: `{}`  
Response `200 OK`:

```json
{
  "registrationId": "REG-2026-000001",
  "issuedAt": "2026-01-01T12:10:00Z"
}
```

If not eligible, `409` or `422` with a stable `REGISTRATION_NOT_ELIGIBLE` code and safe reason. Concurrent/repeated calls for the same applicant return the same ID. The endpoint must not let clients select the year/sequence or bypass server-side payment/verification/admin rules.

### `GET /applicants/me/confirmation`

**Auth:** Applicant. Returns the same final confirmation projection; ID can be `null` until issued.

Response `200 OK`:

```json
{
  "applicantName": "Example Applicant",
  "registrationId": "REG-2026-000001",
  "registrationDate": "2026-01-01T12:10:00Z",
  "registrationStatus": "REGISTERED",
  "paymentStatus": "SUCCESSFUL"
}
```

Confirmation channel (screen, email, SMS), registration date semantics, and final status are pending client confirmation.

## 8. Administrator operations

### `GET /admin/applicants`

**Auth:** Admin role permitting applicant search.  
**Query:** `q`, `registrationStatus`, `paymentStatus`, `page`, `size`, `sort`; allowed values, search semantics, and maximum page size are configured/approved.

Response `200 OK`:

```json
{
  "items": [
    {
      "registrationId": "REG-2026-000001",
      "applicantName": "Example Applicant",
      "mobileMasked": "+•••••••1234",
      "registrationStatus": "REGISTERED",
      "paymentStatus": "SUCCESSFUL",
      "submittedAt": "2026-01-01T12:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

Return minimal fields; no bank account data or document contents. `401`, `403`, `400`, `429`, `500`.

### `GET /admin/applicants/{applicantId}`

**Auth:** Admin read role. Returns approved applicant fields, verification flags/timestamps, registration and payment status; sensitive values remain masked/omitted by default.

Response `200 OK`: `{ "applicant": { "applicantName": "Example Applicant", "mobileMasked": "+•••••••1234", "emailMasked": "a••••••t@example.invalid", "registrationId": "REG-2026-000001", "registrationStatus": "REGISTERED", "paymentStatus": "SUCCESSFUL" } }`

Access to full contact details is role/need-based and pending approval. `404` if unavailable; `403` if role lacks access.

### `GET /admin/applicants/{applicantId}/documents`

**Auth:** Explicit document-review role. Returns document metadata only. If approved, a separate `POST /admin/applicants/{applicantId}/documents/{documentId}/download-link` may issue a short-lived, audit-logged private download link; link TTL and authorization are configuration requirements. Never expose a permanent storage URL.

### `GET /admin/applicants/{applicantId}/bank-details`

**Auth:** Deny by default. Allow only an explicitly approved role with need-to-know; response remains masked unless reveal is explicitly authorized and audited. Who may reveal full data is **PENDING CLIENT CONFIRMATION**. Every access must be audited without recording the account value.

### `PATCH /admin/applicants/{applicantId}/registration-status`

**Auth:** Admin role permitted to change status.

Request:

```json
{
  "status": "APPROVED",
  "reasonCode": "CLIENT_APPROVED_REASON",
  "expectedUpdatedAt": "2026-01-01T12:10:00Z"
}
```

Response `200 OK`: `{ "applicantId": "opaque-reference", "registrationStatus": "APPROVED", "updatedAt": "2026-01-01T12:20:00Z" }`

Validate role, expected current version, and approved transition matrix; conflict on concurrent or illegal transition (`409`). Status vocabulary, transitions, mandatory reason, and audit history are **PENDING CLIENT CONFIRMATION**.

## 9. Cross-cutting validation and operational behavior

- Use Jakarta Bean Validation for DTO-level required/length/format checks, plus service-level workflow checks and database constraints.
- Use consistent `ProblemDetail` exception handling; map known validation, not-found, conflict, authorization, upload, rate-limit, and upstream errors explicitly.
- Correlate requests with a server-generated trace ID. Redact contact identifiers, challenge values, tokens, bank data, uploaded file contents, and provider secrets from logs.
- Rate-limit registration, challenge send/verify, document upload, and login/search endpoints. OTP thresholds are listed above; registration and future module thresholds remain unimplemented and require security review.
- Protect mutating requests against CSRF when cookie authentication is used; configure strict CORS allowlists, TLS, and secure headers.
- API examples are contract proposals, not client-approved business rules. Version/status values must be aligned with the finalization decisions before implementation.

