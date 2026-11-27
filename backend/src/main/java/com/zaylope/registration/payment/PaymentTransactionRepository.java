package com.zaylope.registration.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

	Optional<PaymentTransaction> findByGatewayPaymentId(String gatewayPaymentId);

	List<PaymentTransaction> findByPaymentOrderId(Long paymentOrderId);

	List<PaymentTransaction> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

	Optional<PaymentTransaction> findTopByApplicantIdAndStatusOrderByCreatedAtDesc(Long applicantId, String status);

	@org.springframework.data.jpa.repository.Query("SELECT pt FROM PaymentTransaction pt WHERE "
			+ "(:status = 'ALL' OR pt.status = :status)")
	org.springframework.data.domain.Page<PaymentTransaction> findFiltered(
			@org.springframework.data.repository.query.Param("status") String status,
			org.springframework.data.domain.Pageable pageable);

	@org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(pt.amountPaise), 0) FROM PaymentTransaction pt WHERE pt.status = 'CAPTURED'")
	long sumCapturedRevenuePaise();
}
