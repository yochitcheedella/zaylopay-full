package com.zaylope.registration.applicant;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

	boolean existsByMobileNumberAndMobileVerifiedTrue(String mobileNumber);

	boolean existsByPendingMobileKeyAndApplicantIdNot(String pendingMobileKey, Long applicantId);

	Optional<Applicant> findByMobileNumber(String mobileNumber);

	Optional<Applicant> findByMobileNumberAndMobileVerifiedTrue(String mobileNumber);

	boolean existsByEmail(String email);

    Optional<Applicant> findFirstByEmailOrderByApplicantIdDesc(String email);
    Optional<Applicant> findFirstByMobileNumberOrderByApplicantIdDesc(String mobileNumber);

	Optional<Applicant> findByEmail(String email);

	Optional<Applicant> findByEmailAndEmailVerifiedAtIsNotNull(String email);

	Optional<Applicant> findByEmailIgnoreCaseAndEmailVerifiedAtIsNotNull(String email);

	Optional<Applicant> findByActiveEmailKey(String activeEmailKey);

	@Query("select applicant from Applicant applicant "
			+ "where applicant.emailCaseSensitive = false "
			+ "and lower(applicant.email) = lower(:email) "
			+ "and applicant.emailVerifiedAt is not null")
	Optional<Applicant> findLegacyVerifiedByEmailIgnoreCase(@Param("email") String email);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select applicant from Applicant applicant "
			+ "where applicant.emailCaseSensitive = false "
			+ "and lower(applicant.activeEmailKey) = lower(:activeEmailKey)")
	Optional<Applicant> findLegacyActiveEmailKeyForUpdate(@Param("activeEmailKey") String activeEmailKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select applicant from Applicant applicant where applicant.activeEmailKey = :activeEmailKey")
	Optional<Applicant> findByActiveEmailKeyForUpdate(@Param("activeEmailKey") String activeEmailKey);

	boolean existsByActiveEmailKeyAndApplicantIdNot(String activeEmailKey, Long applicantId);

	boolean existsByEmailAndEmailVerifiedAtIsNotNullAndApplicantIdNot(String email, Long applicantId);

	Optional<Applicant> findByGoogleSub(String googleSub);

	boolean existsByGoogleSub(String googleSub);

	boolean existsByEmailAndApplicantIdNot(String email, Long applicantId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select applicant from Applicant applicant "
			+ "where applicant.pendingMobileKey = :pendingMobileKey")
	Optional<Applicant> findByPendingMobileKeyForUpdate(
			@Param("pendingMobileKey") String pendingMobileKey);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select applicant from Applicant applicant where applicant.applicantId = :applicantId")
	Optional<Applicant> findByIdForUpdate(@Param("applicantId") Long applicantId);

	@Query("SELECT a FROM Applicant a WHERE "
			+ "(:status = 'ALL' OR a.approvalStatus = :status) AND "
			+ "(:paymentStatus = 'ALL' OR a.paymentStatus = :paymentStatus) AND "
			+ "(:search IS NULL OR :search = '' OR "
			+ " LOWER(a.fullName) LIKE LOWER(CONCAT('%', :search, '%')) OR "
			+ " a.mobileNumber LIKE CONCAT('%', :search, '%') OR "
			+ " LOWER(a.email) LIKE LOWER(CONCAT('%', :search, '%')) OR "
			+ " LOWER(a.registrationId) LIKE LOWER(CONCAT('%', :search, '%')) OR "
			+ " LOWER(a.affiliateId) LIKE LOWER(CONCAT('%', :search, '%')))")
	org.springframework.data.domain.Page<Applicant> findFiltered(
			@Param("status") String status,
			@Param("paymentStatus") String paymentStatus,
			@Param("search") String search,
			org.springframework.data.domain.Pageable pageable);

	@Query("SELECT COUNT(a) FROM Applicant a WHERE a.paymentStatus = 'PENDING'")
	long countUnpaidDrafts();

	@Query("SELECT COUNT(a) FROM Applicant a WHERE a.paymentStatus = 'PAID' AND a.approvalStatus = 'PENDING'")
	long countPendingComplianceReviews();

	@Query("SELECT COUNT(a) FROM Applicant a WHERE a.paymentStatus = 'PAID'")
	long countPaidMemberships();

	@Query("SELECT COUNT(a) FROM Applicant a WHERE a.approvalStatus = 'APPROVED'")
	long countApprovedAffiliates();

	@Query("SELECT COUNT(a) FROM Applicant a WHERE a.approvalStatus = 'REJECTED'")
	long countRejectedApplications();
}
