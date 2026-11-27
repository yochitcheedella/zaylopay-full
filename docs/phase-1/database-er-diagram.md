# Phase I MySQL ER Diagram

The diagram includes the initially proposed domain tables, the `registration_id_counters` support table for future ID allocation, and `applicant_access_sessions` for the implemented applicant cookie. Internal primary keys are `BIGINT UNSIGNED`; public registration IDs are separate and nullable until issuance.

> Current implementation note: OTP-related verification designs are historical only. No OTP provider or verification endpoint is active; existing schema and records are preserved.

```mermaid
erDiagram
    APPLICANTS {
        BIGINT applicant_id PK
        VARCHAR registration_id UK "nullable until eligible; unique"
        VARCHAR full_name
        VARCHAR mobile_number "canonical +91; non-unique"
        VARCHAR email_address "non-unique until duplicate policy approved"
        BOOLEAN mobile_verified "default false"
        TIMESTAMP mobile_verified_at "nullable"
        TIMESTAMP email_verified_at "nullable"
        TIMESTAMP privacy_consent_at "nullable"
        VARCHAR privacy_notice_version "nullable"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    APPLICANT_DOCUMENTS {
        BIGINT document_id PK
        BIGINT applicant_id FK
        VARCHAR document_type
        VARCHAR storage_provider
        VARCHAR storage_key UK
        VARCHAR media_type
        BIGINT file_size_bytes
        VARCHAR document_status
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    APPLICANT_BANK_DETAILS {
        BIGINT bank_detail_id PK
        BIGINT applicant_id FK "unique"
        VARCHAR account_holder_name
        VARCHAR bank_name "nullable"
        VARBINARY account_number_ciphertext
        VARCHAR ifsc_code "nullable"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    PAYMENTS {
        BIGINT payment_id PK
        BIGINT applicant_id FK
        VARCHAR idempotency_key UK
        VARCHAR provider_code
        VARCHAR gateway_transaction_reference UK "nullable"
        DECIMAL amount
        CHAR currency
        VARCHAR payment_status
        TIMESTAMP initiated_at
        TIMESTAMP verified_at "nullable"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    OTP_VERIFICATIONS {
        BIGINT otp_verification_id PK
        BIGINT applicant_id FK
        VARCHAR channel "EMAIL only; future design"
        VARBINARY destination_ciphertext
        VARBINARY code_hmac "only if email code approved"
        TIMESTAMP expires_at
        SMALLINT attempt_count
        TIMESTAMP consumed_at "nullable"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    REGISTRATION_STATUS {
        BIGINT registration_status_id PK
        BIGINT applicant_id FK "unique"
        VARCHAR status_code
        VARCHAR status_reason_code "nullable"
        VARCHAR changed_by_subject "nullable"
        TIMESTAMP status_changed_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    REGISTRATION_ID_COUNTERS {
        SMALLINT id_year PK
        INT last_value
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    APPLICANT_ACCESS_SESSIONS {
        BIGINT session_id PK
        BIGINT applicant_id FK
        CHAR token_hash UK
        TIMESTAMP created_at
        TIMESTAMP expires_at
        TIMESTAMP revoked_at "nullable"
    }

    APPLICANTS ||--o{ APPLICANT_DOCUMENTS : has
    APPLICANTS ||--o| APPLICANT_BANK_DETAILS : has
    APPLICANTS ||--o{ PAYMENTS : attempts
    APPLICANTS ||--o{ OTP_VERIFICATIONS : receives
    APPLICANTS ||--o| REGISTRATION_STATUS : has_current_status
    APPLICANTS ||--o{ APPLICANT_ACCESS_SESSIONS : authenticates
```

## Cardinality and design notes

- `||--o{`: each child row belongs to exactly one applicant; an applicant may have zero or many child rows.
- `||--o|`: each optional dependent row belongs to exactly one applicant; an applicant may have zero or one current row. The unique FK on `applicant_id` enforces this 1:1 maximum.
- `registration_id_counters` has no direct FK to applicants; it allocates sequence values transactionally by year.
- Documents point to private storage references; file bytes are not stored in MySQL.
- Bank account ciphertext is application-encrypted and must not be exposed in general applicant responses.
- MSG91 owns SMS OTP generation and verification. No mobile OTP or OTP hash is persisted; `OTP_VERIFICATIONS` is a future email-only design and is not part of V1/V2.
- Registration currently creates an applicant session whose opaque bearer token is sent only in an HttpOnly cookie; MySQL stores only the SHA-256 token hash. V2 defines this table but has not been applied.
- The status table stores current state only. Add a dedicated append-only history entity if the client requires audit history.
- Contact uniqueness, verification prerequisites, document cardinality/limits, status values, and ID issuance conditions are **PENDING CLIENT CONFIRMATION**.

