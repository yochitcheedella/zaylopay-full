ALTER TABLE applicants
    ADD CONSTRAINT uk_applicants_email_address UNIQUE (email_address);
