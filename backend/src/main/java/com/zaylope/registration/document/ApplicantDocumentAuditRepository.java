package com.zaylope.registration.document;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicantDocumentAuditRepository
		extends JpaRepository<ApplicantDocumentAuditEvent, Long> {
}
