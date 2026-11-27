-- Forward-only additive expansion. Existing unbound challenges must be resent.
-- Do not replay on a schema where these columns already exist.
ALTER TABLE applicant_verification_codes
    ADD COLUMN issuance_id CHAR(36) NULL,
    ADD COLUMN destination_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN purpose VARCHAR(32) NOT NULL DEFAULT 'APPLICANT_CONTACT',
    ADD COLUMN challenge_status VARCHAR(24) NOT NULL DEFAULT 'INVALIDATED',
    ADD COLUMN last_sent_at TIMESTAMP(6) NULL,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE otp_abuse_limits (
    bucket_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    window_started_at TIMESTAMP(6) NOT NULL,
    last_sent_at TIMESTAMP(6) NULL,
    request_count INT UNSIGNED NOT NULL DEFAULT 0,
    INDEX ix_otp_abuse_window (window_started_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
