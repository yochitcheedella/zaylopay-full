package com.zaylope.registration.session;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantAccessSessionRepository extends JpaRepository<ApplicantAccessSession, Long> {

	record ActiveSessionProjection(Long applicantId, ApplicantSessionScope sessionScope) {}

	@Query("""
			select session.applicant.applicantId
			from ApplicantAccessSession session
			where session.tokenHash = :tokenHash
				and session.revokedAt is null
				and session.expiresAt > :now
			""")
	Optional<Long> findActiveApplicantId(
			@Param("tokenHash") String tokenHash,
			@Param("now") Instant now);

	@Query("""
			select new com.zaylope.registration.session.ApplicantAccessSessionRepository$ActiveSessionProjection(
				session.applicant.applicantId,
				session.sessionScope
			)
			from ApplicantAccessSession session
			where session.tokenHash = :tokenHash
				and session.revokedAt is null
				and session.expiresAt > :now
			""")
	Optional<ActiveSessionProjection> findActiveSession(
			@Param("tokenHash") String tokenHash,
			@Param("now") Instant now);

	@Modifying
	@Query("delete from ApplicantAccessSession session where session.expiresAt <= :now")
	int deleteExpiredSessions(@Param("now") Instant now);

	Optional<ApplicantAccessSession> findByTokenHash(String tokenHash);

	@Modifying
	@Query("update ApplicantAccessSession session set session.revokedAt = :now "
			+ "where session.applicant.applicantId = :applicantId "
			+ "and session.revokedAt is null")
	int revokeAllByApplicantId(
			@Param("applicantId") Long applicantId,
			@Param("now") Instant now);
}
