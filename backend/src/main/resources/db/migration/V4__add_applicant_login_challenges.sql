CREATE TABLE applicant_login_challenges (
    challenge_id CHAR(36) NOT NULL,
    applicant_id BIGINT UNSIGNED NULL,
    identifier_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    resend_available_at TIMESTAMP(6) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP(6) NULL,
    PRIMARY KEY (challenge_id),
    CONSTRAINT fk_applicant_login_challenge_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id)
        ON DELETE CASCADE,
    INDEX ix_applicant_login_challenge_applicant (applicant_id),
    INDEX ix_applicant_login_challenge_expiry (expires_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
