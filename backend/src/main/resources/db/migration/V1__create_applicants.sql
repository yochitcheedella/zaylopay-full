CREATE TABLE applicants (
    applicant_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    registration_id VARCHAR(20) NULL,
    full_name VARCHAR(150) NOT NULL,
    mobile_number VARCHAR(32) NOT NULL,
    email_address VARCHAR(254) NOT NULL,
    mobile_verified_at TIMESTAMP(6) NULL,
    email_verified_at TIMESTAMP(6) NULL,
    privacy_consent_at TIMESTAMP(6) NULL,
    privacy_notice_version VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (applicant_id),
    CONSTRAINT uk_applicants_registration_id UNIQUE (registration_id),
    INDEX ix_applicants_mobile_number (mobile_number),
    INDEX ix_applicants_email_address (email_address)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
