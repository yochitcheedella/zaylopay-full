# Phase I Registration ID Design

**Status:** Proposed technical mechanism. **ID generation trigger and completion conditions are PENDING CLIENT CONFIRMATION.**

## 1. Public format

Proposed format: `REG-YYYY-XXXXXX`, for example `REG-2026-000001`.

- `YYYY` is the issue year in a client-approved timezone.
- `XXXXXX` is a zero-padded, six-digit, one-based sequence within that year.
- The format is a public reference, not a secret or authorization credential.
- Maximum sequence is `999999` per year at six digits. Exhaustion must fail safely; never wrap/reuse. Whether the format can expand is pending client confirmation.

## 2. Eligibility rule

The system must issue an ID only after the backend evaluates the completion conditions approved by the client. Candidate conditions are mutually exclusive business choices and are not assumed here:

- submission of required form fields;
- successful completion of enabled mobile/email verification;
- successful payment if required;
- document submission or approval if required;
- administrator approval if required.

The issue trigger, whether partially complete applications have an ID, and ID visibility timing are **PENDING CLIENT CONFIRMATION**. Payment browser redirects are never sufficient evidence of payment success; use authenticated webhook/server-side verification.

## 3. Concurrency-safe allocation design

Use `registration_id_counters(id_year PRIMARY KEY, last_value, created_at, updated_at)` and the unique `applicants.registration_id` constraint described in [database-design.md](database-design.md).

Within one MySQL transaction:

1. Lock the target applicant row (`SELECT ... FOR UPDATE`). If `registration_id` is already non-null, return that existing value; this makes retries idempotent.
2. Evaluate the approved eligibility rule from authoritative server/database state. If not eligible, return a domain conflict and allocate nothing.
3. Determine `id_year` from the agreed timezone and issue-time rule.
4. Ensure a counter row exists for `id_year`, using an insert/upsert pattern that locks the unique year row.
5. Increment `last_value` atomically while holding the row lock and read the incremented value. Reject if it exceeds `999999`.
6. Format `REG-{id_year}-{sequence:06d}`.
7. Set `applicants.registration_id` in the same transaction. The database unique constraint is the final integrity guard.
8. Commit. If any step fails, roll back both the counter update and applicant assignment. If the caller retries, step 1 returns the previously committed ID.

Use Spring `@Transactional` around the complete issuance operation. A repository update such as `UPDATE registration_id_counters SET last_value = last_value + 1 WHERE id_year = ?` must be protected by the row lock and checked for overflow. Do not compute `MAX(registration_id) + 1`, use in-memory counters, or rely on an unlocked read.

### Concurrent first allocation for a year

Two transactions may both observe that a year counter does not exist. Create it using a unique-key upsert/insert with conflict-safe locking, then select that row `FOR UPDATE` before incrementing. Verify behavior using the chosen MySQL isolation level and integration tests. A duplicate-key race must be retried within bounded transaction logic or handled by the upsert, not silently ignored.

### Retries and partial failures

- A repeated request for the same applicant returns the same committed ID.
- Applicant-row locking serializes concurrent issuance attempts for one applicant.
- Counter-row locking serializes allocation for a year across applicants.
- `UNIQUE (registration_id)` prevents duplicate public identifiers even if application logic regresses.
- The counter increment and applicant write must share the same transaction, so an exception cannot consume a sequence while leaving the ID unassigned.
- If commit outcome is unknown to the caller, retry by applicant; the persisted value is returned rather than allocating another.

## 4. MySQL and formatting constraints

- Counter primary key: `id_year SMALLINT UNSIGNED`; `last_value INT UNSIGNED NOT NULL DEFAULT 0`.
- Public column: `applicants.registration_id VARCHAR(20) NULL UNIQUE`.
- Exact string length for the proposed format is 15 characters (`REG-` + 4-digit year + `-` + 6 digits), but use a column with modest headroom for future approved format changes.
- Keep leading zeroes only in the public formatted ID. Store the integer counter separately.
- Validate the current year is representable and the format year is four digits.

## 5. Operational and test requirements for implementation

- Test parallel requests for different applicants in the same year: each receives a distinct sequence.
- Test repeated and concurrent requests for one applicant: all return the same ID.
- Test transaction rollback: no duplicate and no phantom consumed number when assignment fails.
- Test counter exhaustion: explicit error and no wrap.
- Test year-boundary behavior in the approved timezone.
- Test unique-index conflict handling as a final guard.
- Log issuance outcome/correlation ID, not contact details or secrets.

## 6. Decisions to approve

1. Exact trigger and required verification/payment/document/admin conditions.
2. Whether an ID is assigned to every submission or only completed/approved registrations.
3. Year boundary timezone and whether it is based on creation, eligibility, or issuance time.
4. Whether sequence resets annually and six-digit width is fixed.
5. Applicant notification channels and when ID becomes visible.
6. What happens when an applicant is rejected, withdrawn, corrected, or re-registered; IDs should not be reused unless explicitly approved.

