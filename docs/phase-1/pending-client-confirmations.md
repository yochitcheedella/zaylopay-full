# Phase I Pending Client Confirmations

> Current implementation note: mobile and email OTP flows are temporarily disabled. Historical MSG91 decisions and settings below do not describe active integrations; new registrations remain unverified.

This is the decision register for the Day 2–3 design and subsequent implementation. The supplied SRS is a draft and the Phase I finalization form is blank; entries marked **Approved** below were confirmed separately and are not inferred from a proposal.

## 1. Business decisions blocking implementation

| ID | Decision required | Why it matters | Current state / proposed safe default |
|---|---|---|---|
| PC-01 | Approve the project stack and resolve the source-document conflict (React + Django + PostgreSQL vs. React + Node.js + PostgreSQL vs. requested React + Java Spring Boot + MySQL). | Determines implementation, deployment, migrations, skills, and team ownership. | **Approved by the project lead for implementation:** React/Vite, Java 21, Spring Boot, MySQL 8, Spring Data JPA/Hibernate, Spring Validation, REST, Flyway, Maven, and Axios. Django, Python backend development, and PostgreSQL are excluded. Spring Security is temporarily removed; applicant sessions remain active and staff authentication remains pending. |
| PC-02 | Define duplicate-registration policy: whether mobile/email are unique, scope/time period, normalization, re-registration, and applicant edit behavior. | Determines DB unique indexes, user messaging, and recovery flow. | **Approved for the current registration API:** reject email duplicates case-insensitively; re-registering an unverified mobile reuses its pending applicant, while an already verified mobile is rejected. Email uniqueness is enforced in code and by additive migration V6. |
| PC-03 | Decide whether mobile verification is a prerequisite for final registration-ID issuance; separately decide whether email verification is required. | Determines final eligibility and email integration. | Mobile verification through the MSG91 widget is implemented at the user's request. Whether it gates ID issuance is pending. Email verification is not implemented and remains undecided. |
| PC-04 | Confirm the MSG91 widget/account configuration (widget ID/tokenAuth, allowed origins/countries, SMS channel), backend Auth Key, sender/template/DLT approval, and arrange a real SMS plus access-token verification test; select an email provider separately if email verification is approved. | Required for live delivery and end-to-end provider validation. | MSG91 OTP Widget is selected for mobile. Credential validity, account ownership alignment, delivery settings, and provider logs have not been verified; no real SMS has been sent. Keep widget credentials in frontend configuration and the account Auth Key in backend-only secrets. |
| PC-05 | Confirm provider-side OTP length/expiry/resend/attempt policy and applicant recovery behavior. | The provider-owned widget controls OTP delivery and code entry; the client must approve the business policy and operational limits. | The application enforces a 30-second widget-start cooldown, 5 starts per number/applicant/hour, 20 starts per IP/hour, 5 backend token validations per number/applicant in the configured five-minute window, and 20 token validations per IP/hour. MSG91 controls each internal widget resend/code attempt. The app limiter is in-memory/JVM-local and must become shared before horizontal scaling. |
| PC-06 | Approve identity document types, maximum count, formats, size limit, applicant replacement/deletion, and whether manual review is required. | Defines UI, validation, storage cost, review workflows, and data exposure. | Only allow a server-side allowlist; no document rule is hard-coded yet. |
| PC-07 | Confirm document review roles, scanning/quarantine expectation, retention/deletion period, and whether review blocks registration. | Sensitive identity data requires controlled access and retention. | Private object storage, restricted access, and no public links; business lifecycle remains pending. |
| PC-08 | Confirm bank-detail purpose and exact fields/requiredness, account jurisdiction/format, update policy, and whether only records are collected. | Defines schema and form validation. | SRS proposes account holder, bank name, account number, confirm number, IFSC; none beyond initial registration is confirmed mandatory. Confirmation input is not persisted. |
| PC-09 | Decide whether any administrator may view full bank details, under what purpose, and whether re-authentication/approval is required. | Defines sensitive RBAC and audit policy. | Deny full reveal by default; dashboard/API returns masked values. |
| PC-10 | Decide whether payment is mandatory, fee, currency, fixed/variable pricing, payment methods, refund/receipt requirements, and retry policy. | Determines checkout, payment state machine, and acceptance tests. | No amount/provider/payment behavior is assumed. The browser never supplies trusted amount/status. |
| PC-11 | Select payment gateway and provide merchant/sandbox credentials, webhook configuration, settlement/test account, and onboarding schedule. | External dependency for gateway work and UAT. | Hosted checkout and signed server-side verification are recommended; provider details pending. |
| PC-12 | Approve registration ID format, issuance trigger, year timezone, sequence reset/width, ID notification, and treatment of withdrawn/rejected/re-registered applicants. | Determines transactional eligibility and public identifier behavior. | Proposal is `REG-YYYY-XXXXXX`, allocated per year with a unique DB constraint; trigger remains pending. |
| PC-13 | Approve final registration/payment/document status values and valid transitions; determine whether admin approval is required. | Controls workflow and status-update APIs. | Status values in the finalization form are proposals only. |
| PC-14 | Define admin identity provider, account provisioning, MFA, roles, search scope, allowed status updates, and audit retention. | Required before exposing any admin operations. | No admin role is trusted by default; enforce least privilege. |
| PC-15 | Confirm applicant session renewal, recovery, logout/revocation, and re-registration behavior. | Needed to secure later uploads, payments, and confirmation retrieval. | Current implementation issues a random applicant-scoped opaque cookie at registration; HttpOnly, SameSite=Strict, `/api/v1`, 30-minute lifetime. The SHA-256 token hash is stored; renewal/recovery/logout UX remains pending. |
| PC-16 | Provide privacy notice, consent wording, legal basis, data residency/jurisdiction, retention/deletion, and breach/contact process. | Required to collect and retain sensitive data. | Store consent evidence only according to approved policy; exact rules pending. |
| PC-17 | Confirm whether the React module is standalone or integrates with an existing application, plus production CORS/origin and cookie settings. | Affects UI/API routing, cookies, navigation, and test environment. | Local CORS defaults to `http://localhost:5173`; production origin and secure-cookie deployment settings remain pending. |
| PC-18 | Confirm expected volume, availability/performance targets, timezone display, deployment environment, storage provider, and backups. | Drives sizing, limits, monitoring, and release plan. | Store UTC timestamps; target SLAs and infrastructure are pending. |
| PC-19 | Approve official brand assets/colors, support contact, accessibility target, and confirmation notification/print requirements. | Needed for production-ready copy and visual design. | Service Intake Sheet was selected for the implemented interface; official brand assets, support content, and remaining requirements are pending. |

### Decisions recorded during implementation

- The public registration response omits the internal `applicantId`; this was explicitly selected by the user. The response currently contains only `message` and acknowledgement `status`.
- The user selected the Service Intake Sheet direction for the UI.
- The user selected Indian national mobile input (10 digits beginning 6–9 or `+91` form) and canonical `+91` storage for this implementation. Email duplicates are rejected case-insensitively; pending mobile re-registration reuses its applicant and verified mobile re-registration is rejected.
- The response value `SUBMITTED` is not persisted as lifecycle state. Persisted registration statuses remain subject to the approved workflow and are not implemented.
- The mobile verification flow uses MSG91's default OTP Widget UI. Backend access-token validation is mocked in automated tests; widget/account configuration and real SMS delivery remain unverified.

## 2. Technical assumptions requiring project approval

These are implementation recommendations, not business policies:

1. Use `BIGINT UNSIGNED AUTO_INCREMENT` for internal MySQL IDs; keep public IDs separate.
2. Add `registration_id_counters` because six tables alone do not provide a robust per-year sequence allocator.
3. Use a unique nullable `applicants.registration_id`; email uniqueness is added by V6 after duplicate-data reconciliation. Preserve the existing pending-mobile key and verified-mobile behavior.
4. Use a one-to-one bank detail row and one-to-one current registration status row; add append-only history only if audit requirements demand it.
5. Do not persist MSG91-managed mobile OTPs, widget access tokens, or their hashes. A future local email challenge must have a separate, approved design.
6. Encrypt bank account numbers at application layer; use private object storage for documents.
7. Verify payment with signed webhook/server-side gateway query; use idempotency and unique gateway references.
8. Store timestamps in UTC and use transactionally locked per-year counters for registration IDs.
9. Keep administrator authentication external/configurable until the client approves its identity provider and roles.

## 3. Source-document and repository observations

- The Software Requirements Specification identifies itself as a draft for client review and leaves key fields/decisions open.
- The finalization document contains blanks for payment, documents, bank fields, ID timing, statuses, dependencies, and sign-off.
- The Day 1–Day 6 document gives conflicting recommendations (Node.js/PostgreSQL and React/Django/PostgreSQL); the project lead later approved the requested Spring Boot/MySQL stack.
- The existing workspace had a Java 21 Spring Boot starter and React/Vite starter. Registration and applicant-scoped sessions are implemented; Spring Security is temporarily disabled. MSG91's default OTP Widget UI and backend access-token validation exist but are not part of registration. Email verification, admin workflows, documents, payments, and ID issuance remain outside the current slice.

## 4. Day 2–3 approval checklist

- [x] Project lead approves Java Spring Boot/MySQL stack against source SRS.
- [x] User confirms duplicate email rejection and existing pending/verified mobile behavior.
- [ ] Client confirms whether mobile verification gates final ID issuance and applicant edit/recovery behavior.
- [ ] Client approves document and bank data requirements, access, and retention.
- [ ] Client selects payment requirement/provider and supplies integration dependencies.
- [ ] Client approves ID format and issue trigger.
- [ ] Client approves registration statuses, admin roles, and status transition rules.
- [ ] Client supplies privacy/consent text and data lifecycle/security decisions.
- [ ] Client approves UI flow, standalone/integration boundary, and acceptance criteria.
- [x] Registration implementation was previously verified against the separate local `zaylope` schema; current read-only inspection found its applicant/session schema is incomplete. See [database-design.md](database-design.md) before further database work.
- [x] MSG91 widget integration and historical migration artifacts are present. Flyway remains disabled; the populated `zaylo_db` schema was not changed.

