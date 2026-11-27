-- Preflight validation script prior to applying V12__harden_auth_identity_verification_and_sessions.sql
-- Read-only checks: must report 0 conflicts in every duplicate-check query before running V12.

SELECT DATABASE() AS target_schema, VERSION() AS mysql_version;

-- 1. Verify Flyway history state
SELECT installed_rank, version, description, type, script, success
FROM flyway_schema_history
ORDER BY installed_rank;

-- 2. Detect any duplicate VERIFIED mobile numbers (must be 0)
SELECT COUNT(*) AS duplicate_verified_mobile_groups
FROM (
    SELECT mobile_number
    FROM applicants
    WHERE mobile_verified = 1
    GROUP BY mobile_number
    HAVING COUNT(*) > 1
) t;

-- 3. Detect any duplicate VERIFIED email addresses case-insensitively (must be 0)
SELECT COUNT(*) AS duplicate_verified_email_groups
FROM (
    SELECT LOWER(email_address)
    FROM applicants
    WHERE email_verified_at IS NOT NULL
    GROUP BY LOWER(email_address)
    HAVING COUNT(*) > 1
) t;

-- 4. Detect any duplicate active email addresses case-insensitively (must be 0)
SELECT COUNT(*) AS duplicate_email_ci_groups
FROM (
    SELECT LOWER(email_address)
    FROM applicants
    GROUP BY LOWER(email_address)
    HAVING COUNT(*) > 1
) t;

-- 5. Detect any duplicate google_sub values (case-sensitive & case-insensitive)
SELECT COUNT(*) AS duplicate_google_sub_cs_groups
FROM (
    SELECT BINARY google_sub
    FROM applicants
    WHERE google_sub IS NOT NULL
    GROUP BY BINARY google_sub
    HAVING COUNT(*) > 1
) t;

SELECT COUNT(*) AS duplicate_google_sub_ci_groups
FROM (
    SELECT LOWER(google_sub)
    FROM applicants
    WHERE google_sub IS NOT NULL
    GROUP BY LOWER(google_sub)
    HAVING COUNT(*) > 1
) t;

-- 6. Inspect constraint differences (V9-baselined vs fresh V1-V11)
SELECT
    EXISTS(SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applicant_login_challenges') AS has_v4_login_challenges,
    EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applicants' AND INDEX_NAME = 'uk_applicants_mobile_number') AS has_v8_uk_mobile,
    EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'applicants' AND INDEX_NAME = 'uk_applicants_email_address') AS has_v6_uk_email;

-- 7. Preview historical email verification classification & legacy sessions to revoke
SELECT
    COUNT(*) AS total_applicants,
    SUM(email_verified_at IS NOT NULL) AS verified_email_count,
    SUM(
        email_verified_at IS NOT NULL AND EXISTS (
            SELECT 1 FROM applicant_verification_codes avc
            WHERE avc.applicant_id = a.applicant_id
              AND avc.verification_channel = 'EMAIL'
              AND avc.purpose = 'APPLICANT_CONTACT'
              AND avc.challenge_status = 'VERIFIED'
              AND avc.consumed_at IS NOT NULL
              AND ABS(TIMESTAMPDIFF(SECOND, avc.consumed_at, a.email_verified_at)) <= 5
        )
    ) AS classified_email_otp,
    SUM(
        email_verified_at IS NOT NULL AND NOT EXISTS (
            SELECT 1 FROM applicant_verification_codes avc
            WHERE avc.applicant_id = a.applicant_id
              AND avc.verification_channel = 'EMAIL'
              AND avc.purpose = 'APPLICANT_CONTACT'
              AND avc.challenge_status = 'VERIFIED'
              AND avc.consumed_at IS NOT NULL
              AND ABS(TIMESTAMPDIFF(SECOND, avc.consumed_at, a.email_verified_at)) <= 5
        )
    ) AS classified_legacy_unknown
FROM applicants a;

SELECT
    COUNT(*) AS total_sessions,
    SUM(revoked_at IS NULL) AS legacy_unrevoked_sessions_to_revoke
FROM applicant_access_sessions;

SELECT COUNT(*) AS mobile_lock_rows FROM mobile_registration_locks;
