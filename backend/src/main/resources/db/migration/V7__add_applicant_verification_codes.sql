CREATE TABLE applicant_verification_codes (
    challenge_id CHAR(36) NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    verification_channel VARCHAR(8) NOT NULL,
    code_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    resend_available_at TIMESTAMP(6) NOT NULL,
    send_window_started_at TIMESTAMP(6) NOT NULL,
    send_count INT UNSIGNED NOT NULL DEFAULT 1,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (challenge_id),
    CONSTRAINT uk_applicant_verification_channel UNIQUE (applicant_id, verification_channel),
    CONSTRAINT fk_applicant_verification_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id)
        ON DELETE CASCADE,
    INDEX ix_applicant_verification_expiry (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
