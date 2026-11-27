package com.zaylope.registration.applicant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicantBankDetailsRepository extends JpaRepository<ApplicantBankDetails, Long> {

	Optional<ApplicantBankDetails> findByApplicantId(Long applicantId);

	boolean existsByApplicantId(Long applicantId);
}
