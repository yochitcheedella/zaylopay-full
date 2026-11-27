package com.zaylope.registration.payment;

import com.zaylope.registration.affiliate.AffiliateIdService;
import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipActivationService;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.affiliate.MembershipActivationPolicy;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.AffiliateAccessDeniedException;
import com.zaylope.registration.common.exception.ApplicationNotSubmittedException;
import com.zaylope.registration.common.exception.InvalidPaymentSignatureException;
import com.zaylope.registration.common.exception.OnboardingConsentRequiredException;
import com.zaylope.registration.common.exception.PaymentAlreadyCompletedException;
import com.zaylope.registration.common.exception.PaymentGatewayException;
import com.zaylope.registration.common.exception.PaymentOrderNotFoundException;
import com.zaylope.registration.config.RazorpayProperties;
import com.zaylope.registration.payment.PaymentDtos.PaymentOrderResponse;
import com.zaylope.registration.payment.PaymentDtos.PaymentVerificationRequest;
import com.zaylope.registration.payment.PaymentDtos.PaymentVerificationResponse;
import com.zaylope.registration.verification.ApplicantVerificationRequiredException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

	private static final Instant NOW = Instant.parse("2026-11-26T10:00:00Z");

	private ApplicantRepository applicantRepository;
	private PaymentOrderRepository paymentOrderRepository;
	private PaymentTransactionRepository paymentTransactionRepository;
	private PaymentWebhookEventRepository webhookEventRepository;
	private RazorpayService razorpayService;
	private AffiliateMembershipRepository membershipRepository;
	private AffiliateIdService affiliateIdService;
	private MembershipActivationPolicy activationPolicy;
	private PaymentPersistenceService paymentPersistenceService;
	private AffiliateMembershipActivationService activationService;
	private PaymentService paymentService;

	@BeforeEach
	void setUp() {
		applicantRepository = Mockito.mock(ApplicantRepository.class);
		paymentOrderRepository = Mockito.mock(PaymentOrderRepository.class);
		paymentTransactionRepository = Mockito.mock(PaymentTransactionRepository.class);
		webhookEventRepository = Mockito.mock(PaymentWebhookEventRepository.class);
		razorpayService = Mockito.mock(RazorpayService.class);
		membershipRepository = Mockito.mock(AffiliateMembershipRepository.class);
		affiliateIdService = Mockito.mock(AffiliateIdService.class);
		activationPolicy = new MembershipActivationPolicy();

		RazorpayProperties properties = new RazorpayProperties(
				"rzp_test_key", "test_secret", "webhook_secret", 59900L, "INR", false, "https://api.razorpay.com/v1");
		when(razorpayService.getProperties()).thenReturn(properties);
		when(affiliateIdService.generateNextAffiliateId()).thenReturn("ZP-261126-001");
		when(affiliateIdService.generateUniqueReferralCode()).thenReturn("ZPABCDEF");
		when(membershipRepository.saveAndFlush(any(AffiliateMembership.class)))
				.thenAnswer(inv -> inv.getArgument(0));
		when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
				.thenAnswer(inv -> inv.getArgument(0));

		paymentPersistenceService = new PaymentPersistenceService(
				applicantRepository, paymentOrderRepository, paymentTransactionRepository);
		activationService = new AffiliateMembershipActivationService(
				applicantRepository,
				membershipRepository,
				paymentTransactionRepository,
				affiliateIdService,
				activationPolicy,
				properties,
				Clock.fixed(NOW, ZoneOffset.UTC));

		paymentService = new PaymentService(
				applicantRepository,
				paymentOrderRepository,
				paymentTransactionRepository,
				webhookEventRepository,
				razorpayService,
				paymentPersistenceService,
				activationService,
				activationPolicy,
				membershipRepository,
				new ObjectMapper());
	}

	private Applicant eligibleSubmittedApplicant() {
		Applicant applicant = new Applicant("Jane Doe", "+919876543210", "jane@example.com");
		applicant.markMobileVerified(NOW);
		applicant.recordPrivacyConsent(NOW, "onboarding-v1");
		applicant.markRegistrationSubmitted(NOW);
		return applicant;
	}

	@Test
	void shouldCreateOrderOf59900PaiseInrForEligibleApplicant() {
		Applicant applicant = eligibleSubmittedApplicant();
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentOrderRepository.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(1L, "CREATED"))
				.thenReturn(Optional.empty());
		when(razorpayService.createOrder(eq(1L), eq(59900L), eq("INR"), any()))
				.thenReturn(new RazorpayService.RazorpayOrderResult(
						"order_test_123", 59900L, "INR", "rcpt_1", "created", "rzp_test_key"));

		PaymentOrderResponse res = paymentService.createOrderForApplicant(1L);

		assertEquals("order_test_123", res.orderId());
		assertEquals(59900L, res.amountPaise());
		assertEquals("INR", res.currency());
		verify(paymentOrderRepository).save(any(PaymentOrder.class));
	}

	@Test
	void shouldReuseUnresolvedOrderAndPreventDuplicateRazorpayOrderCreation() {
		Applicant applicant = eligibleSubmittedApplicant();
		PaymentOrder existing = new PaymentOrder(1L, "order_existing_1", 59900L, "INR", "rcpt_1");
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentOrderRepository.findTopByApplicantIdAndStatusOrderByCreatedAtDesc(1L, "CREATED"))
				.thenReturn(Optional.of(existing));
		when(razorpayService.fetchOrderPayments("order_existing_1")).thenReturn(List.of());

		PaymentOrderResponse res = paymentService.createOrderForApplicant(1L);

		assertEquals("order_existing_1", res.orderId());
		verify(razorpayService, never()).createOrder(any(), any(Long.class), any(), any());
	}

	@Test
	void shouldRejectOrderCreationWhenPrerequisitesMissingOrRestricted() {
		Applicant unverified = new Applicant("Unverified", "+919876543211", "u@example.com");
		when(applicantRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(unverified));
		assertThrows(ApplicantVerificationRequiredException.class, () -> paymentService.createOrderForApplicant(3L));

		Applicant unsubmitted = new Applicant("Unsubmitted", "+919876543212", "d@example.com");
		unsubmitted.markMobileVerified(NOW);
		when(applicantRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(unsubmitted));
		assertThrows(ApplicationNotSubmittedException.class, () -> paymentService.createOrderForApplicant(4L));

		Applicant noConsent = new Applicant("NoConsent", "+919876543213", "c@example.com");
		noConsent.markMobileVerified(NOW);
		noConsent.markRegistrationSubmitted(NOW);
		when(applicantRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(noConsent));
		assertThrows(OnboardingConsentRequiredException.class, () -> paymentService.createOrderForApplicant(5L));

		Applicant rejected = eligibleSubmittedApplicant();
		rejected.markRejected("Policy violation", NOW);
		when(applicantRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(rejected));
		assertThrows(AffiliateAccessDeniedException.class, () -> paymentService.createOrderForApplicant(6L));

		Applicant incompleteDetails = new Applicant("   ", "+919876543214", "inc@example.com");
		incompleteDetails.markMobileVerified(NOW);
		incompleteDetails.recordPrivacyConsent(NOW, "onboarding-v1");
		incompleteDetails.markRegistrationSubmitted(NOW);
		when(applicantRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(incompleteDetails));
		assertThrows(IllegalStateException.class, () -> paymentService.createOrderForApplicant(7L));
	}

	@Test
	void shouldRejectOrderCreationIfAlreadyPaidOrActiveMembershipExists() {
		Applicant paid = eligibleSubmittedApplicant();
		paid.markPaymentPaid();
		AffiliateMembership active = new AffiliateMembership(1L, "ZP-261126-001", "ZPREF1", null, NOW, "LIFETIME");
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(paid));
		when(membershipRepository.findByApplicantId(1L)).thenReturn(Optional.of(active));

		assertThrows(PaymentAlreadyCompletedException.class, () -> paymentService.createOrderForApplicant(1L));
		verify(razorpayService, never()).createOrder(any(), any(Long.class), any(), any());
	}

	@Test
	void shouldVerifyCapturedPaymentAndAutomaticallyActivateMembershipWithoutAdminApproval() {
		Applicant applicant = eligibleSubmittedApplicant();
		PaymentOrder order = new PaymentOrder(1L, "order_123", 59900L, "INR", "rcpt_1");
		PaymentTransaction capturedTx = new PaymentTransaction(
				10L, 1L, "pay_456", "order_123", 59900L, "INR", "upi", "CAPTURED", "valid_sig", true);

		when(applicantRepository.findById(1L)).thenReturn(Optional.of(applicant));
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentOrderRepository.findByGatewayOrderId("order_123")).thenReturn(Optional.of(order));
		when(paymentTransactionRepository.findByGatewayPaymentId("pay_456")).thenReturn(Optional.empty());
		when(razorpayService.verifyPaymentSignature("order_123", "pay_456", "valid_sig")).thenReturn(true);
		when(razorpayService.fetchPayment("pay_456"))
				.thenReturn(new RazorpayService.RazorpayPaymentDetails("pay_456", "captured", 59900L, "INR", "order_123", "upi"));
		when(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(1L))
				.thenReturn(List.of(capturedTx));

		PaymentVerificationResponse res = paymentService.verifyPayment(
				1L, new PaymentVerificationRequest("order_123", "pay_456", "valid_sig"));

		assertTrue(res.verified());
		assertEquals("CAPTURED", res.status());
		assertEquals("ACTIVE", res.membershipStatus());
		assertFalse(res.activationPending());
		assertEquals("ZP-261126-001", res.affiliateId());
		assertTrue(applicant.isPaymentPaid());
		assertEquals("PENDING", applicant.getApprovalStatus(), "Must NOT set approvalStatus=APPROVED on automatic payment activation");
		verify(membershipRepository).saveAndFlush(any(AffiliateMembership.class));
	}

	@Test
	void shouldRejectAuthorizedButUncapturedPaymentAndNotActivateMembership() {
		Applicant applicant = eligibleSubmittedApplicant();
		PaymentOrder order = new PaymentOrder(1L, "order_123", 59900L, "INR", "rcpt_1");

		when(applicantRepository.findById(1L)).thenReturn(Optional.of(applicant));
		when(paymentOrderRepository.findByGatewayOrderId("order_123")).thenReturn(Optional.of(order));
		when(razorpayService.verifyPaymentSignature("order_123", "pay_auth", "valid_sig")).thenReturn(true);
		when(razorpayService.fetchPayment("pay_auth"))
				.thenReturn(new RazorpayService.RazorpayPaymentDetails("pay_auth", "authorized", 59900L, "INR", "order_123"));

		assertThrows(PaymentGatewayException.class,
				() -> paymentService.verifyPayment(1L, new PaymentVerificationRequest("order_123", "pay_auth", "valid_sig")));
		assertFalse(applicant.isPaymentPaid());
		verify(membershipRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldRejectIncorrectAmountCurrencyCrossApplicantOrderAndInvalidSignature() {
		Applicant applicant = eligibleSubmittedApplicant();
		Applicant otherApplicant = eligibleSubmittedApplicant();
		PaymentOrder order = new PaymentOrder(1L, "order_123", 59900L, "INR", "rcpt_1");
		when(applicantRepository.findById(1L)).thenReturn(Optional.of(applicant));
		when(applicantRepository.findById(99L)).thenReturn(Optional.of(otherApplicant));
		when(paymentOrderRepository.findByGatewayOrderId("order_123")).thenReturn(Optional.of(order));

		// Other applicant order
		assertThrows(PaymentOrderNotFoundException.class,
				() -> paymentService.verifyPayment(99L, new PaymentVerificationRequest("order_123", "pay_1", "sig")));

		// Invalid signature
		when(razorpayService.verifyPaymentSignature("order_123", "pay_1", "bad_sig")).thenReturn(false);
		assertThrows(InvalidPaymentSignatureException.class,
				() -> paymentService.verifyPayment(1L, new PaymentVerificationRequest("order_123", "pay_1", "bad_sig")));

		// Wrong amount from gateway
		when(razorpayService.verifyPaymentSignature("order_123", "pay_amt", "valid_sig")).thenReturn(true);
		when(razorpayService.fetchPayment("pay_amt"))
				.thenReturn(new RazorpayService.RazorpayPaymentDetails("pay_amt", "captured", 100L, "INR", "order_123"));
		assertThrows(PaymentGatewayException.class,
				() -> paymentService.verifyPayment(1L, new PaymentVerificationRequest("order_123", "pay_amt", "valid_sig")));

		// Wrong currency from gateway
		when(razorpayService.verifyPaymentSignature("order_123", "pay_cur", "valid_sig")).thenReturn(true);
		when(razorpayService.fetchPayment("pay_cur"))
				.thenReturn(new RazorpayService.RazorpayPaymentDetails("pay_cur", "captured", 59900L, "USD", "order_123"));
		assertThrows(PaymentGatewayException.class,
				() -> paymentService.verifyPayment(1L, new PaymentVerificationRequest("order_123", "pay_cur", "valid_sig")));

		assertFalse(applicant.isPaymentPaid());
		verify(membershipRepository, never()).saveAndFlush(any());
	}

	@Test
	void shouldPreserveCapturedPaymentAsPendingActivationWhenActivationThrows() {
		Applicant applicant = eligibleSubmittedApplicant();
		PaymentOrder order = new PaymentOrder(1L, "order_123", 59900L, "INR", "rcpt_1");
		PaymentTransaction capturedTx = new PaymentTransaction(
				10L, 1L, "pay_rec", "order_123", 59900L, "INR", "upi", "CAPTURED", "valid_sig", true);

		when(applicantRepository.findById(1L)).thenReturn(Optional.of(applicant));
		when(applicantRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(applicant));
		when(paymentOrderRepository.findByGatewayOrderId("order_123")).thenReturn(Optional.of(order));
		when(paymentTransactionRepository.findByGatewayPaymentId("pay_rec")).thenReturn(Optional.empty());
		when(razorpayService.verifyPaymentSignature("order_123", "pay_rec", "valid_sig")).thenReturn(true);
		when(razorpayService.fetchPayment("pay_rec"))
				.thenReturn(new RazorpayService.RazorpayPaymentDetails("pay_rec", "captured", 59900L, "INR", "order_123"));
		when(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(capturedTx));
		doThrow(new RuntimeException("Counter DB temporarily unavailable"))
				.when(affiliateIdService).generateNextAffiliateId();

		PaymentVerificationResponse res = paymentService.verifyPayment(
				1L, new PaymentVerificationRequest("order_123", "pay_rec", "valid_sig"));

		assertTrue(res.verified());
		assertEquals("CAPTURED", res.status());
		assertEquals("PENDING_ACTIVATION", res.membershipStatus());
		assertTrue(res.activationPending());
		assertNull(res.affiliateId());
		assertTrue(applicant.isPaymentPaid(), "Captured payment must stay durably saved");
	}

	@Test
	void webhookRejectsInvalidSignatureSkipsDuplicateAndIgnoresOutOfOrderFailure() {
		when(razorpayService.verifyWebhookSignature("{}", "bad")).thenReturn(false);
		assertThrows(InvalidPaymentSignatureException.class, () -> paymentService.processWebhook("{}", "bad"));

		String failedPayload = """
				{"event_id":"evt_fail_1","event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_f1","order_id":"order_paid_1","error_code":"BAD","error_description":"Failed"}}}}
				""";
		PaymentOrder alreadyPaidOrder = new PaymentOrder(1L, "order_paid_1", 59900L, "INR", "rcpt");
		alreadyPaidOrder.markPaid();
		when(razorpayService.verifyWebhookSignature(failedPayload, "good_sig")).thenReturn(true);
		when(webhookEventRepository.existsById("evt_fail_1")).thenReturn(false);
		when(paymentOrderRepository.findByGatewayOrderId("order_paid_1")).thenReturn(Optional.of(alreadyPaidOrder));

		paymentService.processWebhook(failedPayload, "good_sig");
		assertEquals("PAID", alreadyPaidOrder.getStatus(), "Out-of-order failure webhook must never downgrade PAID order");
		verify(paymentTransactionRepository, never()).saveAndFlush(any());
	}
}
