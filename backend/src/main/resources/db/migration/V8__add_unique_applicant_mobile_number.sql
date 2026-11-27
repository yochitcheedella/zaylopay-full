-- Apply only after checking the target development schema for duplicate mobile numbers.
-- This migration is retained for the future Flyway-baselining effort documented in the README.
ALTER TABLE applicants
    ADD CONSTRAINT uk_applicants_mobile_number UNIQUE (mobile_number);
