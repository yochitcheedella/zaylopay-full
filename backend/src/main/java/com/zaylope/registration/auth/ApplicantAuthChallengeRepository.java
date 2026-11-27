package com.zaylope.registration.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantAuthChallengeRepository extends JpaRepository<ApplicantAuthChallenge, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ApplicantAuthChallenge c where c.issuanceId = :issuanceId")
	Optional<ApplicantAuthChallenge> findByIssuanceIdForUpdate(@Param("issuanceId") String issuanceId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ApplicantAuthChallenge c where c.applicant.applicantId = :applicantId and c.purpose = :purpose")
	Optional<ApplicantAuthChallenge> findByApplicantAndPurposeForUpdate(
			@Param("applicantId") long applicantId,
			@Param("purpose") ApplicantAuthChallenge.Purpose purpose);
}
