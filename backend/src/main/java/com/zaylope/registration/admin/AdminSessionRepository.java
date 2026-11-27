package com.zaylope.registration.admin;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSession, Long> {

	Optional<AdminSession> findByTokenHash(String tokenHash);

	Optional<AdminSession> findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(String tokenHash, Instant now);
}
