package com.zaylope.registration.document;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ApplicantDocumentRepository extends JpaRepository<ApplicantDocument, String> {

	List<ApplicantDocument> findAllByApplicantApplicantIdOrderByUploadedAtAsc(long applicantId);

	Optional<ApplicantDocument> findByDocumentIdAndApplicantApplicantId(
			String documentId,
			long applicantId);

	Optional<ApplicantDocument> findByDocumentId(String documentId);

	List<ApplicantDocument> findAllByApplicantApplicantId(long applicantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<ApplicantDocument> findByApplicantApplicantIdAndDocumentType(
			long applicantId,
			String documentType);

	List<ApplicantDocument> findAllByUploadedAtBefore(Instant cutoff);
}
