package com.zaylope.registration.payment;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.common.exception.PaymentGatewayException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolates durable payment record persistence from membership activation so that
 * a captured ₹599 payment is committed even if downstream Affiliate ID generation
 * encounters a recoverable transient failure.
 */
@Service
public class PaymentPersistenceService {

	private static final Logger LOGGER = LoggerFactory.getLogger(PaymentPersistenceService.class);

	private final ApplicantRepository applicantRepository;
	private final PaymentOrderRepository paymentOrderRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;

	public PaymentPersistenceService(
			ApplicantRepository applicantRepository,
			PaymentOrderRepository paymentOrderRepository,
			PaymentTransactionRepository paymentTransactionRepository) {
		this.applicantRepository = applicantRepository;
		this.paymentOrderRepository = paymentOrderRepository;
		this.paymentTransactionRepository = paymentTransactionRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaymentTransaction recordCapturedPayment(
			Long applicantId,
			String gatewayOrderId,
			String gatewayPaymentId,
			long amountPaise,
			String currency,
			String method,
			String signature,
			boolean signatureVerified) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseGet(() -> applicantRepository.findById(applicantId)
						.orElseThrow(ApplicantNotFoundException::new));

		PaymentOrder order = paymentOrderRepository.findByGatewayOrderId(gatewayOrderId)
				.orElseThrow(() -> new IllegalStateException("Payment order disappeared: " + gatewayOrderId));

		Optional<PaymentTransaction> existingTxOpt = paymentTransactionRepository.findByGatewayPaymentId(gatewayPaymentId);
		if (existingTxOpt.isPresent()) {
			PaymentTransaction existingTx = existingTxOpt.get();
			if (!existingTx.getApplicantId().equals(applicantId)
					|| !existingTx.getGatewayOrderId().equals(gatewayOrderId)) {
				throw new PaymentGatewayException("Payment ID is already associated with another order or applicant.");
			}
			order.markPaid();
			paymentOrderRepository.saveAndFlush(order);
			if (!applicant.isPaymentPaid()) {
				applicant.markPaymentPaid();
				applicantRepository.saveAndFlush(applicant);
			}
			return existingTx;
		}

		// Detect accidental multiple captured payments for the same applicant/membership without silently discarding
		List<PaymentTransaction> priorCaptured = paymentTransactionRepository
				.findByApplicantIdOrderByCreatedAtDesc(applicantId)
				.stream()
				.filter(tx -> "CAPTURED".equalsIgnoreCase(tx.getStatus())
						&& !tx.getGatewayPaymentId().equals(gatewayPaymentId))
				.toList();
		if (!priorCaptured.isEmpty()) {
			LOGGER.warn("Multiple captured membership payments detected for applicantId={}: existingPaymentId={}, newPaymentId={}",
					applicantId, priorCaptured.get(0).getGatewayPaymentId(), gatewayPaymentId);
		}

		PaymentTransaction tx = new PaymentTransaction(
				order.getOrderId(),
				applicantId,
				gatewayPaymentId,
				gatewayOrderId,
				amountPaise,
				currency,
				method,
				"CAPTURED",
				signature,
				signatureVerified);

		tx = paymentTransactionRepository.save(tx);
		if (tx == null) {
			tx = new PaymentTransaction(
					order.getOrderId(),
					applicantId,
					gatewayPaymentId,
					gatewayOrderId,
					amountPaise,
					currency,
					method,
					"CAPTURED",
					signature,
					signatureVerified);
		}

		order.markPaid();
		paymentOrderRepository.save(order);

		applicant.markPaymentPaid();
		applicantRepository.save(applicant);

		return tx;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailedPaymentAttempt(
			Long applicantId,
			Long paymentOrderId,
			String gatewayOrderId,
			String gatewayPaymentId,
			long amountPaise,
			String currency,
			String signature,
			String errorCode,
			String errorDescription) {
		if (gatewayPaymentId == null || gatewayPaymentId.isBlank()) {
			return;
		}
		Optional<PaymentTransaction> existing = paymentTransactionRepository.findByGatewayPaymentId(gatewayPaymentId);
		if (existing.isPresent()) {
			// Never downgrade an already CAPTURED transaction on an out-of-order failure event
			return;
		}

		PaymentTransaction failedTx = new PaymentTransaction(
				paymentOrderId,
				applicantId,
				gatewayPaymentId,
				gatewayOrderId,
				amountPaise,
				currency,
				null,
				"FAILED",
				signature,
				false);
		failedTx.markFailed(errorCode, errorDescription);
		paymentTransactionRepository.saveAndFlush(failedTx);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordRefundedPayment(String gatewayPaymentId, String gatewayOrderId) {
		if (gatewayPaymentId != null && !gatewayPaymentId.isBlank()) {
			paymentTransactionRepository.findByGatewayPaymentId(gatewayPaymentId)
					.ifPresent(tx -> {
						tx.markRefunded("GATEWAY_REFUND", "Refund processed by payment gateway at " + Instant.now());
						paymentTransactionRepository.saveAndFlush(tx);
					});
		}
	}
}
