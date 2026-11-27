ALTER TABLE applicants
    ADD COLUMN registration_submitted_at TIMESTAMP(6) NULL AFTER privacy_notice_version;

UPDATE applicants
SET registration_submitted_at = created_at
WHERE registration_submitted_at IS NULL;

CREATE TABLE applicant_documents (
    document_id CHAR(36) NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    document_type VARCHAR(80) NOT NULL,
    storage_identifier VARCHAR(80) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    file_size BIGINT UNSIGNED NOT NULL,
    uploaded_at TIMESTAMP(6) NOT NULL,
    consent_at TIMESTAMP(6) NOT NULL,
    privacy_notice_version VARCHAR(64) NOT NULL,
    verification_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY (document_id),
    CONSTRAINT uk_applicant_documents_type UNIQUE (applicant_id, document_type),
    CONSTRAINT uk_applicant_documents_storage UNIQUE (storage_identifier),
    CONSTRAINT fk_applicant_documents_applicant
        FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id),
    INDEX ix_applicant_documents_retention (uploaded_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE applicant_document_audit_events (
    audit_event_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    document_id CHAR(36) NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (audit_event_id),
    INDEX ix_applicant_document_audit_document (document_id, occurred_at),
    INDEX ix_applicant_document_audit_applicant (applicant_id, occurred_at)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
