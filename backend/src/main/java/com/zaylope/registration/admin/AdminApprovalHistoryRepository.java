package com.zaylope.registration.admin;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminApprovalHistoryRepository extends JpaRepository<AdminApprovalHistory, Long> {

	List<AdminApprovalHistory> findByApplicantIdOrderByOccurredAtDesc(Long applicantId);

	List<AdminApprovalHistory> findAllByOrderByOccurredAtDesc();

	org.springframework.data.domain.Page<AdminApprovalHistory> findAllByOrderByOccurredAtDesc(org.springframework.data.domain.Pageable pageable);
}
