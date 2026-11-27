ALTER TABLE applicants
    ADD COLUMN pending_mobile_key VARCHAR(32) NULL AFTER mobile_number;

UPDATE applicants AS a
JOIN (
    SELECT mobile_number, MIN(applicant_id) AS keeper_id
    FROM applicants
    WHERE mobile_verified = FALSE
    GROUP BY mobile_number
) AS keepers
    ON keepers.keeper_id = a.applicant_id
SET a.pending_mobile_key = a.mobile_number;

ALTER TABLE applicants
    ADD CONSTRAINT uk_applicants_pending_mobile_key UNIQUE (pending_mobile_key);

CREATE TABLE mobile_registration_locks (
    mobile_number VARCHAR(32) NOT NULL,
    PRIMARY KEY (mobile_number)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO mobile_registration_locks (mobile_number)
SELECT DISTINCT mobile_number
FROM applicants;
