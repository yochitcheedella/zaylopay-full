package com.zaylope.registration.auth;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantOnboardingSessionRepository extends JpaRepository<ApplicantOnboardingSession, Long> {

	Optional<ApplicantOnboardingSession> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from ApplicantOnboardingSession s where s.tokenHash = :tokenHash")
	Optional<ApplicantOnboardingSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	@Modifying
	@Query("delete from ApplicantOnboardingSession s where s.expiresAt <= :now")
	int deleteExpiredSessions(@Param("now") Instant now);
}
