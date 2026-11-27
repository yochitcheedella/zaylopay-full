# Phase I Security and Validation Design

**Status:** Spring Security and CSRF protection are temporarily disabled for local development. A temporary applicant-session filter remains, but it is not a production security configuration. Applicant contact verification and login OTP challenges are implemented; actual delivery depends on explicit provider configuration.

## 1. Applicant data and transport

- Require HTTPS/TLS for all browser, API, storage, and provider communication. Do not accept credentials or sensitive data over plaintext HTTP.
- Minimize collection to approved fields. Display a privacy notice and record consent/version where required; final notice, legal basis, jurisdiction, and retention/deletion schedule are **PENDING CLIENT CONFIRMATION**.
- Reintroduce Spring Security with explicit endpoint authorization, CSRF protection for cookie-authenticated mutations, strict CORS origins, and secure cookie/header settings before production.
- **Implemented applicant access:** successful registration issues a cryptographically random opaque capability in the `ZAYLOPE_APPLICANT` HttpOnly, SameSite=Strict cookie. The cookie is scoped to `/api/v1`; its token is not returned in JSON, and only its SHA-256 hash is stored in `applicant_access_sessions`. Current lifetime is 30 minutes. `APPLICANT_SESSION_SECURE_COOKIE` must be `true` in production (HTTPS); it defaults to `false` for local development.
- Applicant-owned routes derive the applicant ID from the valid session cookie, never from a caller-supplied `applicantId`. Registration is public and does not require an existing applicant session. No mobile-change flow exists; changing a number requires a future approved workflow that clears verification.
- The `/api/v1/csrf` endpoint and CSRF checks are currently removed; do not expose this temporary configuration publicly.
- Session renewal, recovery, logout/revocation UX, and production origin are **PENDING CLIENT CONFIRMATION**. Applicant registration is public and remains susceptible to account/session creation abuse; add a production registration rate limit before deployment.
- Prevent IDOR by deriving applicant ownership from the session cookie, not a client-supplied applicant ID. Check ownership for every document, payment, and bank-data access.
- Do not put PII, OTPs, session tokens, bank information, or document names in URLs, analytics, exception messages, or ordinary logs.

## 2. Login OTP and account enumeration

- `POST /api/v1/auth/login/otp/request` normalizes and validates a single mobile/email identifier. Email identity preserves local-part case and lowercases only the domain. Exact matches win; case-insensitive fallback is restricted to pre-migration legacy records for login continuity and is not used to link Google accounts or reject a differently cased new identity. It resolves verified contacts or the approved email-draft recovery path using parameterized repository methods; login never creates an applicant.
- Durable destination/IP abuse limits are checked before account lookup and apply to eligible, nonexistent, unverified, and restricted identifiers alike.
- Eligibility excludes restricted accounts. The same restriction check is repeated at verification so a restriction added after code issuance cannot result in a new authenticated session.
- Only an eligible account gets a persisted challenge and asynchronous delivery event. The listener runs after transaction commit in `NOT_SUPPORTED`; short locked challenge reads and failure updates use separate `REQUIRES_NEW` transactions, so no database transaction spans an external provider call. Duplicate events are suppressed in-process for the issuance lifetime; delivery is not automatically retried and no outbox is used. A provider failure marks that issuance unusable and is logged without contact or code data.
- For eligible, nonexistent, unverified, restricted, and delivery-unavailable identifiers, the request returns the same HTTP status, generic message, timing metadata, and response fields. Ineligible requests receive an unbacked random challenge reference, never a usable challenge. Provider latency is removed from the request path to reduce timing differences.
- Challenges use cryptographically generated six-digit codes, keyed HMAC storage, issuance/destination/purpose/channel/applicant binding, expiry, attempt/send limits, one-time consumption, and replay/cross-account rejection. Session creation/rotation occurs only after successful challenge verification.
- The frontend masks the user-entered identifier locally and receives no server-provided destination/account-existence signal. Google sign-in continues through the separately verified Google identity and existing account-linking workflow.

## 3. Historical mobile OTP and email verification design (not active)

The following verification details are retained as historical design notes only. Current applicant verification routes and their implementation are described in `docs/otp-verification-day6.md`.

### Implemented mobile verification (MSG91 OTP Widget)

- The browser loads MSG91's official Web SDK and uses its default UI (`exposeMethods: false`). MSG91 generates, delivers, and verifies the OTP; the application does not implement a competing OTP flow.
- The frontend uses `VITE_MSG91_WIDGET_ID` and `VITE_MSG91_TOKEN_AUTH`, which are browser-visible widget configuration, not account Auth Keys. Restrict the widget to approved origins/countries/channels in MSG91. The account Auth Key (`MSG91_AUTH_KEY`) is backend-only and used only to validate the access token.
- The success callback's access token is sent to Spring Boot in the request body, never in a URL. Spring Boot POSTs it to the official Verify Access Token endpoint using the backend-only `authkey` header.
- Treat provider success only as JSON `type=success` plus an explicit returned mobile identity equal to the applicant's current canonical `+91` number. HTTP 200 alone, generic success text, missing identity, or a mismatch is not verification. Provider-specific details and auth key are not returned to the applicant.
- The MSG91 SDK logs callback payloads, including OTP-event details and access-token responses, to browser console methods. The frontend suppresses SDK-tagged payload logs; do not log widget callbacks, access tokens, OTPs, or raw provider payloads elsewhere. Review vendor SDK changes before upgrades.
- Never log or persist OTPs, widget access tokens, or hashes. The OTP remains inside MSG91's widget and provider lifecycle; the access token is transient in browser memory and backend/provider request bodies only.
- On backend token success and exact identity match only, a transactional writer sets `applicants.mobile_verified = true` and `mobile_verified_at` to the current UTC instant. Invalid/rejected tokens, missing identity, mismatches, timeouts, or provider errors leave verification unchanged. Repeating verification after success returns the already-verified state.
- The applicant number is initialized in the widget as `91` plus its 10 national digits. The widget's resulting identity is still checked against the authenticated applicant; an edited or different number cannot be marked verified.
- Server-side limits apply to widget starts (30-second cooldown; 5 per number/applicant/hour; 20 per IP/hour) and backend access-token validation (5 per number/applicant in the configured five-minute window; 20 per IP/hour). The default widget owns internal resend and invalid-code attempts, so MSG91-side controls must also be configured and monitored. The in-memory server limiter is JVM-local and needs shared storage before horizontal scaling.
- Cookie-authenticated widget-start and token-validation mutations must require CSRF protection when Spring Security is reintroduced. Applicant ownership comes from the applicant session; caller-supplied applicant IDs are not accepted.

### Email verification (not implemented)

- Email verification provider/method, whether enabled, and whether it gates registration or ID issuance are **PENDING CLIENT CONFIRMATION**.
- Do not reuse the MSG91 mobile flow or persist SMS OTPs. If a local email code is later approved, design its challenge storage separately using a secret-keyed HMAC, short expiry, attempt cap, one-time consumption, and a secret held outside MySQL. If a provider-managed flow is selected, follow that provider's lifecycle and minimize local challenge state.
- Email verification links, if approved, must use high-entropy single-use tokens, short approved expiry, TLS, and a safe landing flow; redact tokens from referrers and access logs.

## 4. Identity document uploads

- Use private object storage with public access blocked, encryption at rest, least-privilege service credentials, and short-lived authorized download links only where needed.
- Validate file size, extension, actual file signature, detected MIME type, and approved document type on the server. Do not trust client filenames or MIME headers.
- Store opaque object keys and metadata in MySQL, not file BLOBs or permanent public URLs. Use random storage names; avoid applicant names/mobile numbers in storage paths.
- Quarantine and malware-scan uploads before reviewer access where the deployment supports scanning. Scanning vendor/policy, accepted formats/size/count, retention, manual review, and deletion rules are **PENDING CLIENT CONFIRMATION**.
- Restrict reviewer routes by role and applicant assignment/need. Log reviewer identity, document reference, time, and action without logging file contents.
- Protect against path traversal, archive bombs, oversized requests, content sniffing, and accidental inline execution. Prefer attachment disposition for downloads.

## 5. Bank information

- Store account number only as application-layer authenticated ciphertext (for example, an approved AEAD scheme) in `VARBINARY`; keep encryption keys in a managed secret/key service, not source control, DB, or logs.
- Limit decryption to a narrowly scoped service path; prefer showing masked values. Full reveal is denied by default and requires an approved role, purpose, audit event, and business need.
- Never index/search on ciphertext or expose account number in list/search DTOs. Do not persist confirm-account-number input.
- TLS protects transport; database/storage encryption at rest is an additional layer, not a substitute for application-level protection and access control.
- Key rotation, recovery, retention, bank-detail update history, and any need to reveal full data are **PENDING CLIENT CONFIRMATION**.
- Bank-account verification APIs are outside scope unless separately approved.

## 6. Payments and callbacks

- Prefer the payment gateway’s hosted checkout so the application does not handle card credentials. Never store card numbers, CVV, UPI PINs, or banking passwords.
- Keep merchant secrets in a managed secret store and separate sandbox from production credentials.
- Verify webhook signatures against the raw request body; validate timestamp/replay window and provider identity, then process idempotently.
- Treat provider webhooks/server-side status verification as authoritative. Never mark payment successful based solely on a browser redirect or client request.
- Use an idempotency key for payment initiation and a unique provider transaction/event reference for callback deduplication. Apply only valid state transitions; handle repeated/out-of-order callbacks safely.
- Do not return a success-shaped fallback when provider status is unavailable; preserve `PENDING` and reconcile/retry safely.
- Provider, signature algorithm, amount/currency, payment necessity, retry/refund policy, and credentials are **PENDING CLIENT CONFIRMATION**.

## 7. Administrator authentication and authorization

- Require authenticated administrators; do not expose admin functions to the applicant workflow or unauthenticated callers.
- Prefer the client’s approved identity provider or managed identity service; account lifecycle, MFA, password/reset policy, and identity provider are **PENDING CLIENT CONFIRMATION**.
- Apply least privilege, e.g. separate applicant search/read, document review, bank-data access, and status-update permissions. Exact roles and separation-of-duty rules are pending.
- Return minimal fields. Mask contact details and omit bank details/documents unless the specific role has need-to-know authorization.
- Re-authenticate or require stronger assurance for sensitive bank reveal/status actions if required by client policy.
- Audit admin sign-in failures, sensitive data access, document downloads, status changes, and permission changes. Audit records must not include secrets or raw bank values. Audit retention is pending.

## 8. Input validation, API errors, and application hardening

- Validate every request server-side with typed DTOs and Jakarta Bean Validation, then enforce business/workflow rules in services and integrity rules in MySQL.
- Apply allowlists to status codes, document types, callback providers, and sort fields; never concatenate client-controlled SQL/JPQL fragments.
- Enforce request body/multipart limits, rate limits, timeouts, safe output encoding, and server-generated correlation IDs.
- Use a centralized Spring `ProblemDetail` exception handler with stable error codes; map known validation/conflict/access/provider failures explicitly. Unexpected exceptions return generic errors with trace IDs, not stack traces.
- Redact sensitive values in structured logs, traces, metrics, and crash reports. Avoid broad exception catches that convert failed operations into success or hide upstream uncertainty.
- Keep dependencies patched; protect secrets/configuration outside source control; use separate least-privilege DB credentials for runtime and Flyway.
- Security headers and browser CSP should be configured for the deployed UI, with allowed origins and provider redirect behavior reviewed before release.

## 9. Threat-focused implementation checks

- **Duplicate/race:** enforce DB unique constraints for registration IDs; concurrency-safe transaction for allocation.
- **Enumeration:** generic duplicate/challenge responses; throttling and monitoring.
- **Replay:** one-use OTPs, webhook event idempotency, callback timestamp validation.
- **Unauthorized object access:** principal-derived applicant ownership and role checks on every resource.
- **Sensitive data leakage:** inspect logs, API payloads, exception traces, analytics, and browser persistence.
- **Malicious upload:** signature/MIME/size controls, private storage, quarantine/scan, safe download disposition.
- **Privilege abuse:** admin least privilege and immutable audit trail for sensitive actions.

## 10. Client/security-owner decisions

Confirm data purpose/legal basis, privacy wording and versioning, data retention and deletion, applicant session renewal/recovery/logout, production cookie/CORS origins, admin identity/MFA/roles, email verification, production-distributed rate limiting, file policy and scanning, key management/deployment environment, audit retention, and regulatory/security review obligations before production.

