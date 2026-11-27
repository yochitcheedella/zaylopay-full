package com.zaylope.registration.verification;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantVerificationCodeRepository
		extends JpaRepository<ApplicantVerificationCode, String> {
	Optional<ApplicantVerificationCode> findByApplicantApplicantIdAndChannel(long applicantId, ApplicantVerificationChannel channel);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select code from ApplicantVerificationCode code "
			+ "where code.applicant.applicantId = :applicantId and code.channel = :channel")
	Optional<ApplicantVerificationCode> findByApplicantAndChannelForUpdate(
			@Param("applicantId") long applicantId,
			@Param("channel") ApplicantVerificationChannel channel);
}
