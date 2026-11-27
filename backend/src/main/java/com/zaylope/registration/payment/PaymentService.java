package com.zaylope.registration.payment;

import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipActivationService;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.affiliate.MembershipActivationPolicy;
import com.zaylope.registration.affiliate.MembershipLifecycle;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.AffiliateAccessDeniedException;
import com.zaylope.registration.common.exception.ApplicantNotFoundException;
import com.zaylope.registration.common.exception.ApplicationNotSubmittedException;
import com.zaylope.registration.common.exception.InvalidPaymentSignatureException;
import com.zaylope.registration.common.exception.OnboardingConsentRequiredException;
import com.zaylope.registration.common.exception.PaymentAlreadyCompletedException;
import com.zaylope.registration.common.exception.PaymentGatewayException;
import com.zaylope.registration.common.exception.PaymentOrderNotFoundException;
import com.zaylope.registration.payment.PaymentDtos.PaymentOrderResponse;
import com.zaylope.registration.payment.PaymentDtos.PaymentStatusResponse;
import com.zaylope.registration.payment.PaymentDtos.PaymentVerificationRequest;
import com.zaylope.registration.payment.PaymentDtos.PaymentVerificationResponse;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {

	private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);

	private final ApplicantRepository applicantRepository;
	private final PaymentOrderRepository paymentOrderRepository;
	private final PaymentTransactionRepository paymentTransactionRepository;
	private final PaymentWebhookEventRepository webhookEventRepository;
	private final RazorpayService razorpayService;
	private final PaymentPersistenceService paymentPersistenceService;
	private final AffiliateMembershipActivationService activationService;
	private final MembershipActivationPolicy activationPolicy;
	private final AffiliateMembershipRepository membershipRepository;
	private final ObjectMapper objectMapper;

	@Autowired
	public PaymentService(
			ApplicantRepository applicantRepository,
			PaymentOrderRepository paymentOrderRepository,
			PaymentTransactionRepository paymentTransactionRepository,
			PaymentWebhookEventRepository webhookEventRepository,
			RazorpayService razorpayService,
			PaymentPersistenceService paymentPersistenceService,
			AffiliateMembershipActivationService activationService,
			MembershipActivationPolicy activationPolicy,
			AffiliateMembershipRepository membershipRepository,
			ObjectMapper objectMapper) {
		this.applicantRepository = applicantRepository;
		this.paymentOrderRepository = paymentOrderRepository;
		this.paymentTransactionRepository = paymentTransactionRepository;
		this.webhookEventRepository = webhookEventRepository;
		this.razorpayService = razorpayService;
		this.paymentPersistenceService = paymentPersistenceService;
		this.activationService = activationService;
		this.activationPolicy = activationPolicy;
		this.membershipRepository = membershipRepository;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public PaymentOrderResponse createOrderForApplicant(Long applicantId) {
		Applicant applicant = applicantRepository.findByIdForUpdate(applicantId)
				.orElseGet(() -> applicantRepository.findById(applicantId)
						.orElseThrow(ApplicantNotFoundException::new));

		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(applicantId);
		if (activationPolicy.isAccountRestricted(applicant, membershipOpt)) {
			throw new AffiliateAccessDeniedException(
					"Your account is restricted from starting a membership payment. Contact support@zaylope.com.");
		}

		if (membershipOpt.isPresent() && "ACTIVE".equalsIgnoreCase(membershipOpt.get().getStatus())) {
			throw new PaymentAlreadyCompletedException();
		}

		// Prevent charging an applicant who already has a captured transaction or PAID status
		Optional<PaymentTransaction> existingCaptured = activationService.findVerifiedCapturedTransaction(applicantId);
		if (applicant.isPaymentPaid() || existingCaptured.isPresent()) {
			if (!applicant.isPaymentPaid() && existingCaptured.isPresent()) {
				applicant.markPaymentPaid();
				applicantRepository.save(applicant);
			}
			try {
				activationService.activateAfterPayment(applicantId);
			} catch (Exception ex) {
				LOGGER.warn("Deferred activation retry needed during order creation guard for applicantId={}: {}",
						applicantId, ex.getMessage());
			}
			throw new PaymentAlreadyCompletedException();
		}

		if (!applicant.hasValidPersonalDetails()) {
			throw new IllegalStateException("Complete valid personal details before initiating payment.");
		}

		if (!applicant.isContactVerified()) {
			throw new ApplicantVerificationRequiredException();
		}

		if (applicant.getRegistrationSubmittedAt() == null) {
			throw new ApplicationNotSubmittedException();
		}

		if (!applicant.hasPrivacyConsent()) {
			throw new OnboardingConsentRequiredException();
		}

		long expectedAmount = razorpayService.getProperties().amountPaise();
		String expectedCurrency = razorpayService.getProperties().currency();

		// Check if active pending order already exists
		Optional<PaymentOrder> existing = paymentOrderRepository
				.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(applicantId, "CREATED");
		if (existing.isPresent()) {
			PaymentOrder order = existing.get();
			boolean staleMock = order.getGatewayOrderId().startsWith("order_mock_")
					&& !razorpayService.getProperties().mockGateway();
			boolean amountMismatch = order.getAmountPaise() == null
					|| order.getAmountPaise() != expectedAmount
					|| !expectedCurrency.equalsIgnoreCase(order.getCurrency());

			if (staleMock || amountMismatch) {
				LOGGER.info("Replacing stale or mismatched order {} for applicant {}.",
						order.getGatewayOrderId(), applicantId);
				order.markFailed();
				paymentOrderRepository.save(order);
			} else {
				// Reconcile unresolved order with Razorpay before reopening checkout
				if (reconcileCapturedPaymentFromGatewayOrder(applicantId, order, expectedAmount, expectedCurrency)) {
					throw new PaymentAlreadyCompletedException();
				}
				return new PaymentOrderResponse(
						order.getGatewayOrderId(),
						order.getAmountPaise(),
						order.getCurrency(),
						razorpayService.getProperties().keyId(),
						order.getReceipt(),
						order.getStatus());
			}
		}

		String receipt = "rcpt_app_" + applicantId + "_" + System.currentTimeMillis();
		var razorpayOrder = razorpayService.createOrder(applicantId, expectedAmount, expectedCurrency, receipt);

		PaymentOrder order = new PaymentOrder(
				applicantId,
				razorpayOrder.id(),
				expectedAmount,
				expectedCurrency,
				receipt);

		paymentOrderRepository.save(order);

		return new PaymentOrderResponse(
				order.getGatewayOrderId(),
				order.getAmountPaise(),
				order.getCurrency(),
				razorpayOrder.keyId(),
				order.getReceipt(),
				order.getStatus());
	}

	public PaymentVerificationResponse verifyPayment(Long applicantId, PaymentVerificationRequest request) {
		Applicant applicant = applicantRepository.findById(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);

		PaymentOrder order = paymentOrderRepository.findByGatewayOrderId(request.razorpayOrderId())
				.orElseThrow(() -> new PaymentOrderNotFoundException(
						"Payment order not found: " + request.razorpayOrderId()));

		if (!order.getApplicantId().equals(applicantId)) {
			throw new PaymentOrderNotFoundException("Order does not belong to this applicant.");
		}

		long expectedAmount = razorpayService.getProperties().amountPaise();
		String expectedCurrency = razorpayService.getProperties().currency();
		if (order.getAmountPaise() == null
				|| order.getAmountPaise() != expectedAmount
				|| !expectedCurrency.equalsIgnoreCase(order.getCurrency())) {
			throw new PaymentGatewayException("Stored payment order amount or currency does not match membership pricing.");
		}

		// Prevent reusing a gatewayPaymentId owned by another applicant or different order
		Optional<PaymentTransaction> txByPaymentId = paymentTransactionRepository
				.findByGatewayPaymentId(request.razorpayPaymentId());
		if (txByPaymentId.isPresent()) {
			PaymentTransaction existingTx = txByPaymentId.get();
			if (!existingTx.getApplicantId().equals(applicantId)
					|| !existingTx.getGatewayOrderId().equals(order.getGatewayOrderId())) {
				throw new PaymentGatewayException("Payment ID does not belong to this applicant order.");
			}
			if ("CAPTURED".equalsIgnoreCase(existingTx.getStatus()) && existingTx.isSignatureVerified()) {
				return finalizeActivationResponse(applicantId, existingTx);
			}
		}

		// Server-side HMAC signature verification
		boolean signatureValid = razorpayService.verifyPaymentSignature(
				request.razorpayOrderId(),
				request.razorpayPaymentId(),
				request.razorpaySignature());

		if (!signatureValid) {
			paymentPersistenceService.recordFailedPaymentAttempt(
					applicantId,
					order.getOrderId(),
					order.getGatewayOrderId(),
					request.razorpayPaymentId(),
					order.getAmountPaise(),
					order.getCurrency(),
					request.razorpaySignature(),
					"SIGNATURE_MISMATCH",
					"Calculated signature did not match gateway signature.");
			throw new InvalidPaymentSignatureException();
		}

		// Authoritative Razorpay API payment status, amount, currency, and order verification
		String method = "razorpay";
		if (!razorpayService.getProperties().mockGateway()) {
			var paymentDetails = razorpayService.fetchPayment(request.razorpayPaymentId());
			if (!"captured".equalsIgnoreCase(paymentDetails.status())) {
				paymentPersistenceService.recordFailedPaymentAttempt(
						applicantId,
						order.getOrderId(),
						order.getGatewayOrderId(),
						request.razorpayPaymentId(),
						order.getAmountPaise(),
						order.getCurrency(),
						request.razorpaySignature(),
						"PAYMENT_NOT_CAPTURED",
						"Gateway reported non-captured status: " + paymentDetails.status());
				throw new PaymentGatewayException(
						"Payment must be captured before activating membership (current status: "
								+ paymentDetails.status() + ").");
			}
			if (paymentDetails.amountPaise() != order.getAmountPaise()) {
				throw new PaymentGatewayException("Payment amount mismatch: expected "
						+ order.getAmountPaise() + " but received " + paymentDetails.amountPaise());
			}
			if (paymentDetails.currency() == null || !order.getCurrency().equalsIgnoreCase(paymentDetails.currency())) {
				throw new PaymentGatewayException("Payment currency mismatch: expected "
						+ order.getCurrency() + " but received " + paymentDetails.currency());
			}
			if (paymentDetails.orderId() != null && !paymentDetails.orderId().isBlank()
					&& !paymentDetails.orderId().equals(order.getGatewayOrderId())) {
				throw new PaymentGatewayException("Payment order ID mismatch with gateway payment record.");
			}
			if (paymentDetails.method() != null && !paymentDetails.method().isBlank()) {
				method = paymentDetails.method();
			}
		}

		// Persist verified captured payment durably BEFORE attempting membership activation
		PaymentTransaction capturedTx = paymentPersistenceService.recordCapturedPayment(
				applicantId,
				order.getGatewayOrderId(),
				request.razorpayPaymentId(),
				order.getAmountPaise(),
				order.getCurrency(),
				method,
				request.razorpaySignature(),
				true);

		return finalizeActivationResponse(applicantId, capturedTx);
	}

	@Transactional(readOnly = true)
	public PaymentStatusResponse getPaymentStatus(Long applicantId) {
		Applicant applicant = applicantRepository.findById(applicantId)
				.orElseThrow(ApplicantNotFoundException::new);

		Optional<AffiliateMembership> membershipOpt = membershipRepository.findByApplicantId(applicantId);
		MembershipLifecycle.MembershipStatus membershipStatus =
				activationService.deriveMembershipStatus(applicant, membershipOpt);
		boolean activationPending = membershipStatus == MembershipLifecycle.MembershipStatus.PENDING_ACTIVATION;

		Optional<PaymentTransaction> successfulTx = activationService.findVerifiedCapturedTransaction(applicantId);
		if (successfulTx.isPresent()) {
			PaymentTransaction tx = successfulTx.get();
			return new PaymentStatusResponse(
					"PAID",
					true,
					tx.getGatewayOrderId(),
					tx.getGatewayPaymentId(),
					tx.getAmountPaise(),
					tx.getCapturedAt(),
					tx.getCurrency(),
					membershipStatus.name(),
					activationPending,
					membershipOpt.map(AffiliateMembership::getAffiliateId).orElse(null),
					membershipOpt.map(AffiliateMembership::getActivatedAt).orElse(null),
					null);
		}

		Optional<PaymentOrder> latestOrder = paymentOrderRepository
				.findTopByApplicantIdOrderByCreatedAtDesc(applicantId);
		Optional<PaymentTransaction> latestFailedTx = paymentTransactionRepository
				.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(applicantId, "FAILED");
		Optional<PaymentTransaction> latestRefundedTx = paymentTransactionRepository
				.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(applicantId, "REFUNDED");

		String effectivePaymentStatus = applicant.getPaymentStatus();
		String lastFailureReason = null;
		if (latestRefundedTx.isPresent() && !applicant.isPaymentPaid()) {
			effectivePaymentStatus = "REFUNDED";
		} else if (!applicant.isPaymentPaid() && latestFailedTx.isPresent()) {
			effectivePaymentStatus = "FAILED";
			lastFailureReason = latestFailedTx.get().getErrorDescription();
		}

		return new PaymentStatusResponse(
				effectivePaymentStatus,
				applicant.isPaymentPaid(),
				latestOrder.map(PaymentOrder::getGatewayOrderId).orElse(null),
				null,
				latestOrder.map(PaymentOrder::getAmountPaise).orElse(razorpayService.getProperties().amountPaise()),
				null,
				razorpayService.getProperties().currency(),
				membershipStatus.name(),
				activationPending,
				membershipOpt.map(AffiliateMembership::getAffiliateId).orElse(null),
				membershipOpt.map(AffiliateMembership::getActivatedAt).orElse(null),
				lastFailureReason);
	}

	@Transactional
	public void processWebhook(String rawPayload, String signatureHeader) {
		boolean verified = razorpayService.verifyWebhookSignature(rawPayload, signatureHeader);
		if (!verified) {
			LOGGER.warn("Webhook signature validation failed.");
			throw new InvalidPaymentSignatureException();
		}

		JsonNode root;
		try {
			root = objectMapper.readTree(rawPayload);
		} catch (Exception e) {
			throw new PaymentGatewayException("Malformed webhook JSON payload.", e);
		}

		String event = root.path("event").asText("");
		String eventId = root.path("event_id").asText("");
		if (eventId == null || eventId.isBlank()) {
			eventId = hashPayload(rawPayload);
		}

		if (webhookEventRepository.existsById(eventId)) {
			LOGGER.info("Webhook event {} already processed, skipping duplicate.", eventId);
			return;
		}

		PaymentWebhookEvent webhookEvent = new PaymentWebhookEvent(eventId, event, hashPayload(rawPayload));

		if ("payment.captured".equalsIgnoreCase(event) || "order.paid".equalsIgnoreCase(event)) {
			JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
			JsonNode orderEntity = root.path("payload").path("order").path("entity");
			String orderId = paymentEntity.path("order_id").asText("");
			if (orderId.isBlank()) {
				orderId = orderEntity.path("id").asText("");
			}
			String paymentId = paymentEntity.path("id").asText("");
			String status = paymentEntity.path("status").asText("order.paid".equalsIgnoreCase(event) ? "captured" : "");
			long amount = paymentEntity.path("amount").asLong(0L);
			if (amount <= 0L && orderEntity.has("amount")) {
				amount = orderEntity.path("amount").asLong(0L);
			}
			String currency = paymentEntity.path("currency").asText("");
			if (currency.isBlank()) {
				currency = orderEntity.path("currency").asText("INR");
			}
			String method = paymentEntity.path("method").asText("webhook");

			if (!orderId.isBlank() && !paymentId.isBlank() && "captured".equalsIgnoreCase(status)) {
				Optional<PaymentOrder> orderOpt = paymentOrderRepository.findByGatewayOrderId(orderId);
				if (orderOpt.isPresent()) {
					PaymentOrder order = orderOpt.get();
					if (amount == order.getAmountPaise() && order.getCurrency().equalsIgnoreCase(currency)) {
						paymentPersistenceService.recordCapturedPayment(
								order.getApplicantId(),
								order.getGatewayOrderId(),
								paymentId,
								amount,
								currency,
								method,
								signatureHeader,
								true);
						try {
							activationService.activateAfterPayment(order.getApplicantId());
						} catch (Exception ex) {
							LOGGER.warn("Captured webhook payment recorded for applicantId={} but activation deferred: {}",
									order.getApplicantId(), ex.getMessage());
						}
					} else {
						LOGGER.warn("Webhook amount/currency mismatch for order {}: got {} {}, expected {} {}",
								orderId, amount, currency, order.getAmountPaise(), order.getCurrency());
						webhookEvent.markFailed("Webhook amount/currency mismatch");
					}
				}
			}
		} else if ("payment.failed".equalsIgnoreCase(event)) {
			JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
			String orderId = paymentEntity.path("order_id").asText("");
			String paymentId = paymentEntity.path("id").asText("");
			String errCode = paymentEntity.path("error_code").asText("PAYMENT_FAILED");
			String errDesc = paymentEntity.path("error_description").asText("Payment failed at gateway.");
			if (!orderId.isBlank()) {
				paymentOrderRepository.findByGatewayOrderId(orderId).ifPresent(order -> {
					if (!"PAID".equalsIgnoreCase(order.getStatus())) {
						paymentPersistenceService.recordFailedPaymentAttempt(
								order.getApplicantId(),
								order.getOrderId(),
								order.getGatewayOrderId(),
								paymentId,
								order.getAmountPaise(),
								order.getCurrency(),
								signatureHeader,
								errCode,
								errDesc);
					}
				});
			}
		} else if ("refund.processed".equalsIgnoreCase(event) || "payment.refunded".equalsIgnoreCase(event)) {
			JsonNode refundEntity = root.path("payload").path("refund").path("entity");
			JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
			String paymentId = refundEntity.path("payment_id").asText(paymentEntity.path("id").asText(""));
			String orderId = paymentEntity.path("order_id").asText("");
			paymentPersistenceService.recordRefundedPayment(paymentId, orderId);
		}

		if (webhookEvent.getProcessingError() == null) {
			webhookEvent.markProcessed();
		}
		webhookEventRepository.save(webhookEvent);
		LOGGER.info("Processed webhook event {} type {}", eventId, event);
	}

	private PaymentVerificationResponse finalizeActivationResponse(Long applicantId, PaymentTransaction capturedTx) {
		try {
			var activation = activationService.activateAfterPayment(applicantId);
			AffiliateMembership membership = activation.membership();
			String membershipStatus = activation.restricted()
					? MembershipLifecycle.MembershipStatus.REVOKED.name()
					: MembershipLifecycle.MembershipStatus.ACTIVE.name();
			return new PaymentVerificationResponse(
					true,
					capturedTx.getGatewayOrderId(),
					capturedTx.getGatewayPaymentId(),
					capturedTx.getStatus(),
					capturedTx.getAmountPaise(),
					capturedTx.getCapturedAt(),
					membershipStatus,
					false,
					membership.getAffiliateId(),
					membership.getReferralCode(),
					membership.getActivatedAt());
		} catch (Exception ex) {
			// Captured payment MUST stay persisted; report recoverable activation pending state
			LOGGER.warn("Captured payment verified for applicantId={} but activation pending: {}",
					applicantId, ex.getMessage());
			return new PaymentVerificationResponse(
					true,
					capturedTx.getGatewayOrderId(),
					capturedTx.getGatewayPaymentId(),
					capturedTx.getStatus(),
					capturedTx.getAmountPaise(),
					capturedTx.getCapturedAt(),
					MembershipLifecycle.MembershipStatus.PENDING_ACTIVATION.name(),
					true,
					null,
					null,
					null);
		}
	}

	private boolean reconcileCapturedPaymentFromGatewayOrder(
			Long applicantId,
			PaymentOrder order,
			long expectedAmount,
			String expectedCurrency) {
		List<RazorpayService.RazorpayPaymentDetails> gatewayPayments =
				razorpayService.fetchOrderPayments(order.getGatewayOrderId());
		for (var payment : gatewayPayments) {
			if ("captured".equalsIgnoreCase(payment.status())
					&& payment.amountPaise() == expectedAmount
					&& expectedCurrency.equalsIgnoreCase(payment.currency())) {
				paymentPersistenceService.recordCapturedPayment(
						applicantId,
						order.getGatewayOrderId(),
						payment.paymentId(),
						expectedAmount,
						expectedCurrency,
						payment.method() != null ? payment.method() : "razorpay",
						null,
						true);
				try {
					activationService.activateAfterPayment(applicantId);
				} catch (Exception ex) {
					LOGGER.warn("Order reconciliation captured payment for applicantId={} with deferred activation: {}",
							applicantId, ex.getMessage());
				}
				return true;
			}
		}
		return false;
	}

	private String hashPayload(String payload) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (Exception e) {
			return UUID.randomUUID().toString().replace("-", "");
		}
	}
}
