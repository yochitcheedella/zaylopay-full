# Phase I MySQL Database Design

**Status:** Proposed normalized design baseline. Flyway is currently disabled. The local `zaylope` schema's `applicants` columns match the entity, but registration support tables were missing and caused MySQL error 1146 during registration. The additive [manual repair script](../../backend/src/main/resources/db/manual/registration-support-tables.sql) created `mobile_registration_locks` and `applicant_access_sessions` without changing applicant rows. After confirming no case-insensitive duplicate emails, the unique email constraint from [V6](../../backend/src/main/resources/db/migration/V6__add_unique_applicant_email.sql) was applied manually. One unverified test applicant was added during the live smoke test; there were no pre-existing applicant rows. Flyway history remains absent, and other migration-defined tables are not reconciled. Reconcile and baseline the actual schema before re-enabling Flyway. The current endpoint returns `SUBMITTED` as an acknowledgement and does not persist a `registration_status` row.
**Target:** MySQL 8 with InnoDB, `utf8mb4`, Spring Data JPA/Hibernate, and Flyway migrations at implementation time.

> Current implementation note: all OTP integrations and endpoints are disabled. Verification columns and historical migration files are retained. The test applicant remains unverified.

## 1. Design decisions

- **Internal identifiers:** `BIGINT UNSIGNED AUTO_INCREMENT` primary keys. They are compact and straightforward for a single MySQL service; they are internal identifiers and must not be used as public registration references. External APIs should not expose applicant IDs without authorization.
- **Registration IDs:** Separate nullable public `registration_id`, uniquely indexed on `applicants`. It remains `NULL` until the agreed issue trigger is met.
- **Counter support table:** Add `registration_id_counters` beyond the six initially considered tables. A per-year locked counter is needed to generate `REG-YYYY-XXXXXX` safely under concurrency.
- **Engine/encoding/time:** InnoDB; `utf8mb4`; UTC application/database convention; `TIMESTAMP(6)` for audit times. Set database/session timezone to UTC.
- **Status columns:** `VARCHAR`, not MySQL `ENUM`, so approved lifecycle changes can be migrated without coupling historical data to a database enum. Approved values and transitions remain client decisions.
- **Audit:** Each mutable entity has `created_at` and `updated_at`. The status table is a current-state row (not an event history); if immutable audit history is required, approve an additional history/audit table.
- **Data minimization:** Documents live in private object storage. MSG91 owns mobile OTP generation and verification; do not persist SMS OTPs or their hashes. A locally managed email-verification challenge, if later approved, must have a separately approved design. Bank account values are application-encrypted ciphertext. Do not store payment-card credentials.
- **Constraints:** All child FKs use `ON DELETE RESTRICT` by default to avoid silently deleting regulated/sensitive records. Email identity preserves local-part case and normalizes the domain to lowercase. V13 adds case-sensitive applicant email keys while marking existing rows as legacy case-insensitive identities; rollout requires V12 first and explicit database approval. Retention and deletion policy is pending; implement explicit, authorized retention workflows only after approval.

## 2. Table definitions

Types below use MySQL 8 syntax. Names are logical design names; precise JPA mappings and migration syntax are implementation work.

### 2.1 `applicants`

One row per applicant identity/profile. Email uniqueness is enforced case-insensitively; mobile duplication follows the pending/verified registration behavior.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `registration_id` | `VARCHAR(20)` | `NULL`, no default | Unique `uk_applicants_registration_id`; nullable before issuance. |
| `full_name` | `VARCHAR(150)` | `NOT NULL` | Required by SRS. Length/character policy needs client confirmation. |
| `mobile_number` | `VARCHAR(32)` | `NOT NULL` | Required by SRS. Current implementation accepts valid Indian 10-digit national form or `+91` form, normalizes to `+91` plus 10 digits, and stores that canonical value. A verified mobile is rejected; a pending mobile reuses its applicant. |
| `email_address` | `VARCHAR(254)` | `NOT NULL` | Required by SRS. Canonical identity preserves local-part case and lowercases the domain; V13 changes the column collation to case-sensitive. |
| `mobile_verified` | `BOOLEAN` | `NOT NULL DEFAULT FALSE` | Defined by V2 and present in the local schema; V2 is not recorded in Flyway history. Set only after MSG91 returns JSON `type=success`. |
| `mobile_verified_at` | `TIMESTAMP(6)` | `NULL` | Set only after successful verification. |
| `email_verified_at` | `TIMESTAMP(6)` | `NULL` | Set only after successful verification. |
| `privacy_consent_at` | `TIMESTAMP(6)` | `NULL` | Evidence timestamp if required by approved notice/consent policy. |
| `privacy_notice_version` | `VARCHAR(64)` | `NULL` | Notice version associated with consent; wording/version pending. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Creation audit timestamp. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Update audit timestamp. |

**Indexes/constraints**

- `PRIMARY KEY (applicant_id)`
- `UNIQUE (registration_id)`; multiple `NULL` values are permitted by MySQL.
- `UNIQUE uk_applicants_email_address (email_address)`; added by V6 after duplicate reconciliation.
- `INDEX ix_applicants_mobile_number (mobile_number)`
- `INDEX ix_applicants_email_address (email_address)`
- `mobile_verified` and `mobile_verified_at` must agree: false/null before verification; true/non-null only after the backend confirms provider success.

**V6 rollout plan:** The V6 constraint was applied manually to the local `zaylope` schema after a case-insensitive duplicate check returned zero groups. It is not recorded in Flyway history. For any other database, back up the target, inspect and resolve duplicate values with the data owner without deleting applicant rows, reconcile the schema with V1-V5, and establish a valid baseline before enabling migrations.

**V13 identity rollout:** V13 depends on V12. It adds `email_case_sensitive` with `FALSE` for existing rows, switches applicant email identity columns to binary collation, and rebuilds the generated verified-email key to preserve local-part case while normalizing the domain. It does not rewrite applicant email values. New/changed email addresses are marked case-sensitive; login alone may use the explicit legacy fallback when no exact match exists. Do not execute V12/V13 against the main database without approval and a reviewed rollout plan.

### 2.2 `applicant_documents`

One applicant may have zero or more document references. File bytes remain in private object storage.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `document_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL` | FK to `applicants.applicant_id` |
| `document_type` | `VARCHAR(64)` | `NOT NULL` | Approved code; allowed values pending. |
| `storage_provider` | `VARCHAR(32)` | `NOT NULL` | Private storage adapter name; deployment selection pending. |
| `storage_key` | `VARCHAR(512)` | `NOT NULL` | Opaque private object key, never a public URL; unique. |
| `original_file_name` | `VARCHAR(255)` | `NULL` | Optional display metadata; not a path. |
| `media_type` | `VARCHAR(127)` | `NOT NULL` | Server-detected type, not trusted client header. |
| `file_size_bytes` | `BIGINT UNSIGNED` | `NOT NULL` | Positive size; maximum pending. |
| `document_status` | `VARCHAR(32)` | `NOT NULL` | Workflow status; allowed values/review need pending. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Upload time. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Metadata update time. |

**Indexes/constraints:** PK; `UNIQUE (storage_key)`; FK `applicant_id` with `ON DELETE RESTRICT`; `INDEX ix_documents_applicant_created (applicant_id, created_at)`.

### 2.3 `applicant_bank_details`

Zero or one current bank-detail record per applicant. A separate history table is not proposed unless bank-detail changes must be audited.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `bank_detail_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL` | Unique FK to `applicants.applicant_id`; enforces 1:1. |
| `account_holder_name` | `VARCHAR(150)` | `NOT NULL` | Requiredness pending client confirmation. |
| `bank_name` | `VARCHAR(120)` | `NULL` | Whether required/free text/list is pending. |
| `account_number_ciphertext` | `VARBINARY(512)` | `NOT NULL` | Application-layer authenticated encryption; never persist plaintext. |
| `ifsc_code` | `VARCHAR(11)` | `NULL` | Requiredness/jurisdiction and format pending. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Creation time. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Update time. |

**Indexes/constraints:** PK; `UNIQUE (applicant_id)`; FK with `ON DELETE RESTRICT`. Do not index encrypted account numbers. The client must confirm actual required fields before migration; the example is not a commitment that all bank fields are mandatory.

### 2.4 `payments`

An applicant may have zero or more payment attempts/transactions.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `payment_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL` | FK to applicant. |
| `idempotency_key` | `VARCHAR(128)` | `NOT NULL` | Unique per internal payment initiation request. |
| `provider_code` | `VARCHAR(32)` | `NOT NULL` | Client-selected gateway code; provider pending. |
| `gateway_transaction_reference` | `VARCHAR(128)` | `NULL` | Unique when present; provider reference. |
| `amount` | `DECIMAL(12,2)` | `NOT NULL` | Server-configured amount; fee pending. |
| `currency` | `CHAR(3)` | `NOT NULL` | ISO 4217 code; currency pending. |
| `payment_status` | `VARCHAR(24)` | `NOT NULL` | Proposed SRS values: Initiated, Pending, Successful, Failed, Cancelled, Refunded (if applicable). |
| `failure_code` | `VARCHAR(64)` | `NULL` | Sanitized provider/application code; no sensitive details. |
| `failure_message` | `VARCHAR(255)` | `NULL` | Sanitized, length-limited message; never expose raw provider payload. |
| `initiated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Initiation time. |
| `verified_at` | `TIMESTAMP(6)` | `NULL` | Server-side status confirmation time. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Creation audit timestamp. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Update audit timestamp. |

**Indexes/constraints:** PK; `UNIQUE (idempotency_key)`; `UNIQUE (gateway_transaction_reference)` (nullable); FK `applicant_id` with `ON DELETE RESTRICT`; `INDEX ix_payments_applicant_created (applicant_id, created_at)`; `INDEX ix_payments_status_created (payment_status, created_at)`.

Never store PAN/CVV, UPI PIN, bank credentials, or raw payment credentials. Persist only references and status metadata required for reconciliation.

### 2.5 `otp_verifications` (future email verification only)

This proposed table is **not used for MSG91 mobile OTP**. MSG91 owns the SMS OTP lifecycle, and this application neither stores the OTP nor a hash of it. Keep this table out of migrations until an email-verification method is approved; if email verification uses provider-managed links or codes, redesign this table accordingly.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `otp_verification_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL` | FK to applicant. |
| `channel` | `VARCHAR(8)` | `NOT NULL` | `EMAIL` only for a future locally managed email challenge. |
| `destination_ciphertext` | `VARBINARY(512)` | `NOT NULL` | Encrypt destination at application layer; avoid plaintext PII duplication. |
| `code_hmac` | `VARBINARY(64)` | `NOT NULL` | HMAC/digest only if a locally managed email code is explicitly approved. Never use for MSG91 mobile OTP. |
| `expires_at` | `TIMESTAMP(6)` | `NOT NULL` | Challenge expiry; TTL pending. |
| `attempt_count` | `SMALLINT UNSIGNED` | `NOT NULL DEFAULT 0` | Failed-attempt count; limit pending. |
| `consumed_at` | `TIMESTAMP(6)` | `NULL` | Non-null after successful one-time use. |
| `provider_reference` | `VARCHAR(128)` | `NULL` | Optional delivery reference; no OTP content. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Challenge creation time. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Challenge state update time. |

**Indexes/constraints:** PK; FK `applicant_id` with `ON DELETE RESTRICT`; `INDEX ix_otp_applicant_channel_created (applicant_id, channel, created_at)`; `INDEX ix_otp_expiry (expires_at)`. Enforce `channel = EMAIL` in application validation and, where supported, a MySQL `CHECK`.

### 2.6 `registration_status`

One current status row per applicant. This table is a snapshot, not an audit event stream.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `registration_status_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL` | Unique FK to applicant; enforces 1:1. |
| `status_code` | `VARCHAR(32)` | `NOT NULL` | Initial value and allowed lifecycle values pending client approval. |
| `status_reason_code` | `VARCHAR(64)` | `NULL` | Optional approved reason code; no unrestricted sensitive notes. |
| `changed_by_subject` | `VARCHAR(255)` | `NULL` | Stable, non-sensitive actor subject for admin actions; exact identity source pending. |
| `status_changed_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Last transition time. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Row creation time. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Last row update. |

**Indexes/constraints:** PK; `UNIQUE (applicant_id)`; FK with `ON DELETE RESTRICT`; `INDEX ix_registration_status_code_changed (status_code, status_changed_at)`.

If requirements include a complete status-change audit, add a separately approved append-only history table with actor, prior/new state, timestamp, and correlation ID. Do not treat `updated_at` as a complete audit trail.

### 2.7 `registration_id_counters` (support table)

Year-keyed transactional counter required for the proposed ID format.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `id_year` | `SMALLINT UNSIGNED` | `NOT NULL` | PK; year associated with generated ID. |
| `last_value` | `INT UNSIGNED` | `NOT NULL DEFAULT 0` | Last allocated sequence; enforce maximum `999999` for six digits. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Row creation time. |
| `updated_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)` | Counter update time. |

**Indexes/constraints:** `PRIMARY KEY (id_year)`. Counter is internal, updated only inside the ID issuance transaction. Sequence exhaustion must fail explicitly; never wrap or reuse a number. Whether the width may grow beyond six digits is pending confirmation.

### 2.8 `applicant_access_sessions`

Short-lived, revocable applicant-scoped capability sessions. Registration issues the opaque token in an HttpOnly cookie; only its SHA-256 hash is persisted. The plaintext token is never returned in JSON or stored in MySQL.

| Column | MySQL type | Null/default | Keys / notes |
|---|---|---|---|
| `session_id` | `BIGINT UNSIGNED` | `NOT NULL`, auto-increment | PK |
| `applicant_id` | `BIGINT UNSIGNED` | `NOT NULL` | FK to `applicants.applicant_id`; `ON DELETE RESTRICT`. |
| `token_hash` | `CHAR(64)` ASCII, binary collation | `NOT NULL` | Unique SHA-256 hex digest; never the bearer token. |
| `created_at` | `TIMESTAMP(6)` | `NOT NULL DEFAULT CURRENT_TIMESTAMP(6)` | Session creation time. |
| `expires_at` | `TIMESTAMP(6)` | `NOT NULL` | Current implementation uses a 30-minute lifetime. |
| `revoked_at` | `TIMESTAMP(6)` | `NULL` | Non-null when revoked. |

**Indexes/constraints:** PK; `UNIQUE (token_hash)`; FK to applicant; indexes on `applicant_id` and `expires_at`. Expired-session cleanup is scheduled by the backend. Renewal and recovery behavior remain unimplemented.

## 3. Relationships and deletion

- `applicants` 1 — 0..N `applicant_documents`
- `applicants` 1 — 0..1 `applicant_bank_details`
- `applicants` 1 — 0..N `payments`
- `applicants` 1 — 0..N `otp_verifications` (future locally managed email verification only; never MSG91 mobile OTP)
- `applicants` 1 — 0..1 `registration_status`
- `applicants` 1 — 0..N `applicant_access_sessions`
- `registration_id_counters` is a year-keyed allocator and is not FK-linked to each applicant; the year/sequence is represented in `registration_id`.

Use explicit retention/deletion procedures after the client defines legal and operational policy. Do not cascade delete applicant PII or storage objects by default.

## 4. Persistence and migration notes

- Use Flyway versioned migrations for schema changes; migration execution is outside this design task.
- V1-V5 describe the historical applicant, verification, session, lock, login-challenge, and document schema. The local schema currently lacks the session and mobile-registration-lock tables and has no Flyway history. Compare all existing columns and tables against these scripts before establishing a Flyway baseline; never replay them blindly.
- Never apply migrations against the process environment's current `DB_URL` unless the target is explicitly verified; it points to the populated, unrelated `zaylo_db` schema. No database or Flyway command was run for the MSG91 change. The earlier registration smoke test used the separate `zaylope` schema; its test record remains.
- The prior live registration smoke test predates the current read-only schema inspection. The local `zaylope` schema currently has zero applicant rows and is not ready for a live registration because required lock/session tables are absent. A separate local schema, `zaylo_db`, contains unrelated data; do not point migrations at it.
- Use JPA `@Version` optimistic locking on mutable aggregates if concurrent admin edits are supported; this is an implementation recommendation, not a schema requirement.
- Ensure app-level encryption keys/HMAC pepper are managed outside source control and the database (for example, a managed secret/key service selected for the deployment).
- Validate all constraints in the API and retain database unique/FK constraints as the concurrency and integrity authority.
- MySQL version must be 8.0; if using `CHECK` constraints, require a release that enforces them (MySQL 8.0.16+).

