package com.zaylope.registration.affiliate;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AffiliateMembershipRepository extends JpaRepository<AffiliateMembership, Long> {

	Optional<AffiliateMembership> findByApplicantId(Long applicantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT m FROM AffiliateMembership m WHERE m.applicantId = :applicantId")
	Optional<AffiliateMembership> findByApplicantIdForUpdate(@Param("applicantId") Long applicantId);

	Optional<AffiliateMembership> findByAffiliateId(String affiliateId);

	Optional<AffiliateMembership> findByReferralCode(String referralCode);

	boolean existsByReferralCode(String referralCode);

	boolean existsByAffiliateId(String affiliateId);

	@org.springframework.data.jpa.repository.Query("SELECT m FROM AffiliateMembership m WHERE "
			+ "(:search IS NULL OR :search = '' OR "
			+ " LOWER(m.affiliateId) LIKE LOWER(CONCAT('%', :search, '%')) OR "
			+ " LOWER(m.referralCode) LIKE LOWER(CONCAT('%', :search, '%')))")
	org.springframework.data.domain.Page<AffiliateMembership> findFiltered(
			@org.springframework.data.repository.query.Param("search") String search,
			org.springframework.data.domain.Pageable pageable);
}
