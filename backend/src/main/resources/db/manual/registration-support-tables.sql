-- Manual, additive repair for schemas created before registration support tables existed.
-- This file is not run automatically; Flyway remains disabled.
-- Apply the V6 email-uniqueness constraint separately after checking for duplicates.

CREATE TABLE IF NOT EXISTS mobile_registration_locks (
    mobile_number VARCHAR(32) NOT NULL,
    PRIMARY KEY (mobile_number)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO mobile_registration_locks (mobile_number)
SELECT DISTINCT mobile_number
FROM applicants;

CREATE TABLE IF NOT EXISTS applicant_access_sessions (
    session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    applicant_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT uk_applicant_access_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_applicant_access_sessions_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id)
        ON DELETE RESTRICT,
    INDEX ix_applicant_access_sessions_applicant (applicant_id),
    INDEX ix_applicant_access_sessions_expires (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
