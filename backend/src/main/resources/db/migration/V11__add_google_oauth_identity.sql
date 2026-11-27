-- V11: Add Google OAuth identity fields to applicants
ALTER TABLE applicants
    ADD COLUMN google_sub VARCHAR(255) NULL AFTER email_address,
    ADD COLUMN auth_provider VARCHAR(32) NOT NULL DEFAULT 'LOCAL' AFTER google_sub,
    ADD COLUMN google_email VARCHAR(254) NULL AFTER auth_provider,
    ADD COLUMN google_picture_url VARCHAR(512) NULL AFTER google_email;

ALTER TABLE applicants
    ADD CONSTRAINT uk_applicants_google_sub UNIQUE (google_sub);

CREATE INDEX ix_applicants_google_sub ON applicants (google_sub);
