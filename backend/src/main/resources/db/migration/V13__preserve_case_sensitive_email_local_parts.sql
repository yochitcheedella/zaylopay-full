-- Preserve email local-part case as part of applicant identity.
-- Existing rows remain legacy case-insensitive identities; new/changed emails are case-sensitive.

ALTER TABLE applicants
    ADD COLUMN email_case_sensitive BOOLEAN NOT NULL DEFAULT FALSE AFTER email_address;

ALTER TABLE applicants
    DROP INDEX uk_applicants_verified_email,
    DROP COLUMN verified_email_key;

ALTER TABLE applicants
    MODIFY COLUMN email_address VARCHAR(254)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    MODIFY COLUMN active_email_key VARCHAR(254)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    MODIFY COLUMN google_email VARCHAR(254)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;

ALTER TABLE applicants
    ADD COLUMN verified_email_key VARCHAR(254)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin
        GENERATED ALWAYS AS (
            CASE
                WHEN email_verified_at IS NULL THEN NULL
                WHEN LOCATE('@', email_address) = 0 THEN email_address
                ELSE CONCAT(
                    SUBSTRING(email_address, 1, LOCATE('@', email_address)),
                    LOWER(SUBSTRING(email_address, LOCATE('@', email_address) + 1))
                )
            END
        ) STORED AFTER pending_email_expires_at,
    ADD CONSTRAINT uk_applicants_verified_email UNIQUE (verified_email_key);
