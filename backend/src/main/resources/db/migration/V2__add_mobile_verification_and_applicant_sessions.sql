ALTER TABLE applicants
    ADD COLUMN mobile_verified BOOLEAN NOT NULL DEFAULT FALSE AFTER mobile_number;

CREATE TABLE applicant_access_sessions (
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
