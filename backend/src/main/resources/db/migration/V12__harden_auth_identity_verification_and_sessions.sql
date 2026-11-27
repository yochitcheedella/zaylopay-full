-- V12: Harden authentication, Google identity provenance, verified contact uniqueness,
-- expiring pending reservations, server-side onboarding sessions, and auth challenges.
-- Forward-only additive migration compatible with both V9-baselined schemas and fresh V1-V11 installs.

-- 1. Revoke all legacy applicant sessions (previous login allowed unverified session creation)
UPDATE applicant_access_sessions
SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP(6))
WHERE revoked_at IS NULL;

ALTER TABLE applicant_access_sessions
    ADD COLUMN session_scope VARCHAR(24) NOT NULL DEFAULT 'ONBOARDING' AFTER token_hash,
    ADD INDEX ix_applicant_access_sessions_scope (applicant_id, session_scope);

-- 2. Add provenance, reservation expiry, active email key, generated verified-contact keys,
--    and enforce case-sensitive collation on google_sub before dropping legacy constraints.
ALTER TABLE applicants
    ADD COLUMN email_verification_method VARCHAR(24) NOT NULL DEFAULT 'NONE' AFTER email_verified_at,
    ADD COLUMN pending_mobile_expires_at TIMESTAMP(6) NULL AFTER pending_mobile_key,
    ADD COLUMN active_email_key VARCHAR(254) NULL AFTER email_address,
    ADD COLUMN pending_email_expires_at TIMESTAMP(6) NULL AFTER active_email_key,
    MODIFY COLUMN google_sub VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    ADD COLUMN verified_mobile_key VARCHAR(32) GENERATED ALWAYS AS (
        CASE WHEN mobile_verified = 1 THEN mobile_number ELSE NULL END
    ) STORED AFTER pending_mobile_expires_at,
    ADD COLUMN verified_email_key VARCHAR(254) GENERATED ALWAYS AS (
        CASE WHEN email_verified_at IS NOT NULL THEN LOWER(email_address) ELSE NULL END
    ) STORED AFTER pending_email_expires_at;

-- 3. Conservative historical provenance backfill:
--    Only classify as EMAIL_OTP when a VERIFIED EMAIL OTP challenge corresponds to the current email_verified_at.
--    Never classify as GOOGLE solely because google_sub is present; use LEGACY_UNKNOWN where unverified/mock-capable.
UPDATE applicants a
SET a.email_verification_method = CASE
    WHEN a.email_verified_at IS NULL THEN 'NONE'
    WHEN EXISTS (
        SELECT 1
        FROM applicant_verification_codes avc
        WHERE avc.applicant_id = a.applicant_id
          AND avc.verification_channel = 'EMAIL'
          AND avc.purpose = 'APPLICANT_CONTACT'
          AND avc.challenge_status = 'VERIFIED'
          AND avc.consumed_at IS NOT NULL
          AND ABS(TIMESTAMPDIFF(SECOND, avc.consumed_at, a.email_verified_at)) <= 5
    ) THEN 'EMAIL_OTP'
    ELSE 'LEGACY_UNKNOWN'
END;

-- 4. Reconcile active_email_key, pending_mobile_key, and 30-minute reservation expiries.
--    Verified emails keep active_email_key permanently; unverified emails/mobiles only reserve for 30 minutes.
UPDATE applicants
SET active_email_key = CASE
        WHEN email_verified_at IS NOT NULL THEN LOWER(email_address)
        WHEN DATE_ADD(created_at, INTERVAL 30 MINUTE) > CURRENT_TIMESTAMP(6) THEN LOWER(email_address)
        ELSE NULL
    END,
    pending_email_expires_at = CASE
        WHEN email_verified_at IS NOT NULL THEN NULL
        WHEN DATE_ADD(created_at, INTERVAL 30 MINUTE) > CURRENT_TIMESTAMP(6)
            THEN DATE_ADD(created_at, INTERVAL 30 MINUTE)
        ELSE NULL
    END,
    pending_mobile_key = CASE
        WHEN mobile_verified = 1 THEN NULL
        WHEN email_verified_at IS NOT NULL OR registration_submitted_at IS NOT NULL OR payment_status <> 'PENDING' THEN NULL
        WHEN DATE_ADD(created_at, INTERVAL 30 MINUTE) > CURRENT_TIMESTAMP(6) THEN pending_mobile_key
        ELSE NULL
    END,
    pending_mobile_expires_at = CASE
        WHEN mobile_verified = 1 THEN NULL
        WHEN email_verified_at IS NOT NULL OR registration_submitted_at IS NOT NULL OR payment_status <> 'PENDING' THEN NULL
        WHEN pending_mobile_key IS NOT NULL AND DATE_ADD(created_at, INTERVAL 30 MINUTE) > CURRENT_TIMESTAMP(6)
            THEN DATE_ADD(created_at, INTERVAL 30 MINUTE)
        ELSE NULL
    END;

-- 5. Add replacement verified-contact and active-reservation unique constraints BEFORE dropping legacy constraints.
ALTER TABLE applicants
    ADD CONSTRAINT uk_applicants_verified_mobile UNIQUE (verified_mobile_key),
    ADD CONSTRAINT uk_applicants_verified_email UNIQUE (verified_email_key),
    ADD CONSTRAINT uk_applicants_active_email_key UNIQUE (active_email_key);

-- 6. Conditionally drop legacy unconditional mobile/email unique indexes so unverified contacts cannot permanently block owners.
SET @drop_uk_mobile_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'applicants'
              AND INDEX_NAME = 'uk_applicants_mobile_number'
        ),
        'ALTER TABLE applicants DROP INDEX uk_applicants_mobile_number',
        'SELECT 1'
    )
);
PREPARE stmt_drop_uk_mobile FROM @drop_uk_mobile_sql;
EXECUTE stmt_drop_uk_mobile;
DEALLOCATE PREPARE stmt_drop_uk_mobile;

SET @drop_uk_email_sql := (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'applicants'
              AND INDEX_NAME = 'uk_applicants_email_address'
        ),
        'ALTER TABLE applicants DROP INDEX uk_applicants_email_address',
        'SELECT 1'
    )
);
PREPARE stmt_drop_uk_email FROM @drop_uk_email_sql;
EXECUTE stmt_drop_uk_email;
DEALLOCATE PREPARE stmt_drop_uk_email;

-- 7. Reconcile mobile_registration_locks with ownership and expiry
ALTER TABLE mobile_registration_locks
    ADD COLUMN applicant_id BIGINT UNSIGNED NULL AFTER mobile_number,
    ADD COLUMN expires_at TIMESTAMP(6) NULL AFTER applicant_id,
    ADD INDEX ix_mobile_locks_expires (expires_at);

UPDATE mobile_registration_locks l
LEFT JOIN applicants av ON av.verified_mobile_key = l.mobile_number
LEFT JOIN applicants ap ON ap.pending_mobile_key = l.mobile_number
SET l.applicant_id = COALESCE(av.applicant_id, ap.applicant_id),
    l.expires_at = CASE
        WHEN av.applicant_id IS NOT NULL THEN NULL
        WHEN ap.applicant_id IS NOT NULL THEN ap.pending_mobile_expires_at
        ELSE CURRENT_TIMESTAMP(6)
    END;

DELETE FROM mobile_registration_locks
WHERE applicant_id IS NULL;

-- 8. Server-side short-lived Google onboarding & linking sessions (prevents raw ID tokens in client state)
CREATE TABLE IF NOT EXISTS applicant_onboarding_sessions (
    onboarding_session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    flow_type VARCHAR(32) NOT NULL,
    google_sub VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    google_email VARCHAR(254) NOT NULL,
    google_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    google_hosted_domain VARCHAR(253) NULL,
    google_name VARCHAR(150) NULL,
    google_picture_url VARCHAR(512) NULL,
    target_applicant_id BIGINT UNSIGNED NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (onboarding_session_id),
    CONSTRAINT uk_onboarding_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_onboarding_sessions_target FOREIGN KEY (target_applicant_id) REFERENCES applicants (applicant_id) ON DELETE RESTRICT,
    INDEX ix_onboarding_sessions_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 9. Authentication, draft-recovery, and Google-linking OTP challenges
CREATE TABLE IF NOT EXISTS applicant_auth_challenges (
    challenge_id CHAR(36) NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    purpose VARCHAR(24) NOT NULL,
    verification_channel VARCHAR(8) NOT NULL,
    destination_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issuance_id CHAR(36) NOT NULL,
    challenge_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    send_count INT UNSIGNED NOT NULL DEFAULT 1,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    send_window_started_at TIMESTAMP(6) NOT NULL,
    last_sent_at TIMESTAMP(6) NOT NULL,
    resend_available_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    consumed_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (challenge_id),
    CONSTRAINT uk_auth_challenge_applicant_purpose UNIQUE (applicant_id, purpose),
    CONSTRAINT uk_auth_challenge_issuance UNIQUE (issuance_id),
    CONSTRAINT fk_auth_challenge_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id) ON DELETE CASCADE,
    INDEX ix_auth_challenge_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 10. Remove obsolete unused V4 table if present on fresh V1-V11 installs
DROP TABLE IF EXISTS applicant_login_challenges;
