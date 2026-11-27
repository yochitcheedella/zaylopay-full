package com.zaylope.registration.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

	Optional<PaymentOrder> findByGatewayOrderId(String gatewayOrderId);

	List<PaymentOrder> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);

	Optional<PaymentOrder> findTopByApplicantIdAndStatusOrderByCreatedAtDesc(Long applicantId, String status);

	Optional<PaymentOrder> findTopByApplicantIdOrderByCreatedAtDesc(Long applicantId);
}
