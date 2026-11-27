package com.zaylope.registration.affiliate;

import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.auth.ApplicantLoginController;
import com.zaylope.registration.common.exception.PaymentAlreadyCompletedException;
import com.zaylope.registration.payment.PaymentDtos;
import com.zaylope.registration.payment.PaymentService;
import com.zaylope.registration.payment.PaymentTransactionRepository;
import com.zaylope.registration.registration.ApplicantRegistrationRequest;
import com.zaylope.registration.registration.ApplicantRegistrationWriter;
import com.zaylope.registration.verification.ApplicantContactMasker;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MembershipActivationMySqlIntegrationTest {

	private static final String TEST_DB_NAME = "zaylope_activation_test";
	private static final String KEY_SECRET = "activation_int_test_key_secret";
	private static final String WEBHOOK_SECRET = "activation_int_test_webhook_secret";
	private static final AtomicInteger MOBILE_SEQ = new AtomicInteger(200000);

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		String host = System.getenv("TEST_DB_HOST");
		if (host == null || host.isBlank()) host = "127.0.0.1";
		String port = System.getenv("TEST_DB_PORT");
		if (port == null || port.isBlank()) port = "3306";
		String baseUrl = "jdbc:mysql://" + host + ":" + port + "/?allowPublicKeyRetrieval=true&sslMode=DISABLED";
		String testDbUrl = "jdbc:mysql://" + host + ":" + port + "/" + TEST_DB_NAME
				+ "?allowPublicKeyRetrieval=true&sslMode=DISABLED&connectionTimeZone=UTC";

		String user = System.getenv("TEST_DB_USERNAME");
		if (user == null || user.isBlank()) user = System.getenv("DB_USERNAME");
		if (user == null || user.isBlank()) user = "root";
		String pass = System.getenv("TEST_DB_PASSWORD");
		if (pass == null) pass = System.getenv("DB_PASSWORD");
		if (pass == null) pass = "";

		try (Connection conn = DriverManager.getConnection(baseUrl, user, pass);
			 Statement stmt = conn.createStatement()) {
			stmt.execute("DROP DATABASE IF EXISTS " + TEST_DB_NAME);
			stmt.execute("CREATE DATABASE " + TEST_DB_NAME
					+ " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
		} catch (Exception e) {
			throw new RuntimeException("Failed to create isolated MySQL test DB", e);
		}

		Flyway.configure()
				.dataSource(testDbUrl, user, pass)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load()
				.migrate();

		final String finalUser = user;
		final String finalPass = pass;
		registry.add("spring.datasource.url", () -> testDbUrl);
		registry.add("spring.datasource.username", () -> finalUser);
		registry.add("spring.datasource.password", () -> finalPass);
		registry.add("spring.flyway.enabled", () -> false);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("app.documents.enabled", () -> false);
		registry.add("razorpay.mock-gateway", () -> true);
		registry.add("razorpay.key-id", () -> "rzp_test_int_key");
		registry.add("razorpay.key-secret", () -> KEY_SECRET);
		registry.add("razorpay.webhook-secret", () -> WEBHOOK_SECRET);
		registry.add("razorpay.amount-paise", () -> 59900L);
		registry.add("razorpay.currency", () -> "INR");
	}

	@Autowired ApplicantRepository applicantRepository;
	@Autowired ApplicantRegistrationWriter registrationWriter;
	@Autowired PaymentService paymentService;
	@Autowired PaymentTransactionRepository paymentTransactionRepository;
	@Autowired AffiliateMembershipRepository membershipRepository;
	@Autowired AffiliateMembershipActivationService activationService;
	@Autowired JdbcTemplate jdbcTemplate;

	private Applicant createVerifiedSubmittedApplicant(String name, String emailPrefix) {
		int suffix = MOBILE_SEQ.incrementAndGet();
		String mobile = "9988" + String.format("%06d", suffix);
		String email = emailPrefix + suffix + "@example.com";
		registrationWriter.register(new ApplicantRegistrationRequest(name, mobile, email));
		Applicant applicant = applicantRepository.findByEmail(email).orElseThrow();
		Instant now = Instant.now();
		applicant.markMobileVerified(now);
		applicant.recordPrivacyConsent(now, "onboarding-v1");
		applicant.markRegistrationSubmitted(now);
		return applicantRepository.saveAndFlush(applicant);
	}

	private static String hmacSha256(String data, String secret) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void paymentActivatesPermanentAffiliateIdWithoutAdminApprovalAndSurvivesRetriesAndProfileEdits() throws Exception {
		Applicant applicant = createVerifiedSubmittedApplicant("Rohan Mehta", "rohan");
		long applicantId = applicant.getApplicantId();

		// 1. Before payment: no Affiliate ID exists
		assertTrue(membershipRepository.findByApplicantId(applicantId).isEmpty());

		// 2. Create Razorpay order & verify captured ₹599 payment with real HMAC-SHA256 signature
		PaymentDtos.PaymentOrderResponse order = paymentService.createOrderForApplicant(applicantId);
		assertEquals(59900L, order.amountPaise());
		assertEquals("INR", order.currency());

		String paymentId = "pay_int_" + applicantId;
		String sig = hmacSha256(order.orderId() + "|" + paymentId, KEY_SECRET);
		PaymentDtos.PaymentVerificationResponse verified = paymentService.verifyPayment(
				applicantId,
				new PaymentDtos.PaymentVerificationRequest(order.orderId(), paymentId, sig));

		assertTrue(verified.verified());
		assertEquals("CAPTURED", verified.status());
		assertEquals("ACTIVE", verified.membershipStatus());
		assertFalse(verified.activationPending());
		assertNotNull(verified.affiliateId());

		String istDatePart = LocalDate.now(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyMMdd"));
		assertTrue(verified.affiliateId().matches("^ZP-" + istDatePart + "-\\d{3,}$"));

		// 3. Database state check: approvalStatus stays PENDING (NOT artificially APPROVED)
		Applicant reloaded = applicantRepository.findById(applicantId).orElseThrow();
		assertEquals("PAID", reloaded.getPaymentStatus());
		assertEquals("PENDING", reloaded.getApprovalStatus());
		AffiliateMembership persistedMembership = membershipRepository.findByApplicantId(applicantId).orElseThrow();
		assertEquals(verified.affiliateId(), persistedMembership.getAffiliateId());
		assertNull(persistedMembership.getApprovalId());

		// 4. Paid applicant cannot initiate another charge
		assertThrows(PaymentAlreadyCompletedException.class, () -> paymentService.createOrderForApplicant(applicantId));

		// 5. Duplicate verify callback + duplicate captured webhook + recovery endpoint keep identical ID
		PaymentDtos.PaymentVerificationResponse repeatVerify = paymentService.verifyPayment(
				applicantId, new PaymentDtos.PaymentVerificationRequest(order.orderId(), paymentId, sig));
		assertEquals(verified.affiliateId(), repeatVerify.affiliateId());

		String webhookPayload = """
				{"event_id":"evt_cap_%d","event":"payment.captured","payload":{"payment":{"entity":{"id":"%s","order_id":"%s","amount":59900,"currency":"INR","status":"captured","method":"upi"}}}}
				""".formatted(applicantId, paymentId, order.orderId());
		String webhookSig = hmacSha256(webhookPayload, WEBHOOK_SECRET);
		paymentService.processWebhook(webhookPayload, webhookSig);
		paymentService.processWebhook(webhookPayload, webhookSig); // duplicate delivery

		var recoveryResult = activationService.activateAfterPayment(applicantId);
		assertFalse(recoveryResult.newlyActivated());
		assertEquals(verified.affiliateId(), recoveryResult.membership().getAffiliateId());

		// 6. Profile email change preserves permanent Affiliate ID
		reloaded.changeEmailAddress("rohan.updated" + applicantId + "@example.com", Instant.now().plusSeconds(300));
		reloaded.markEmailVerified(Instant.now(), Applicant.EMAIL_METHOD_OTP);
		applicantRepository.saveAndFlush(reloaded);
		assertEquals(verified.affiliateId(),
				membershipRepository.findByApplicantId(applicantId).orElseThrow().getAffiliateId());
	}

	@Test
	void concurrentActivationsForSameApplicantIssueExactlyOneIdentity() throws Exception {
		Applicant applicant = createVerifiedSubmittedApplicant("Concurrent Single", "concsingle");
		long applicantId = applicant.getApplicantId();
		PaymentDtos.PaymentOrderResponse order = paymentService.createOrderForApplicant(applicantId);
		String paymentId = "pay_conc_single_" + applicantId;
		String sig = hmacSha256(order.orderId() + "|" + paymentId, KEY_SECRET);

		// First persist captured payment, or run concurrent verifyPayment calls
		int threads = 8;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		Set<String> observedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
		List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

		for (int i = 0; i < threads; i++) {
			pool.submit(() -> {
				try {
					start.await();
					var res = paymentService.verifyPayment(
							applicantId,
							new PaymentDtos.PaymentVerificationRequest(order.orderId(), paymentId, sig));
					if (res.affiliateId() != null) {
						observedIds.add(res.affiliateId());
					}
				} catch (Throwable t) {
					errors.add(t);
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertTrue(done.await(15, TimeUnit.SECONDS));
		pool.shutdown();

		// DB must contain strictly one affiliate_memberships row for this applicant
		Integer count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM affiliate_memberships WHERE applicant_id = ?", Integer.class, applicantId);
		assertEquals(1, count);
		assertEquals(1, observedIds.size(), "Concurrent callbacks must produce one identity, errors=" + errors);
	}

	@Test
	void concurrentActivationsForMultipleApplicantsProduceUniqueSequentialIds() throws Exception {
		int applicantsCount = 10;
		List<Long> applicantIds = new ArrayList<>();
		List<PaymentDtos.PaymentOrderResponse> orders = new ArrayList<>();
		for (int i = 0; i < applicantsCount; i++) {
			Applicant app = createVerifiedSubmittedApplicant("Multi Applicant " + (char) ('A' + i), "multi" + i);
			applicantIds.add(app.getApplicantId());
			orders.add(paymentService.createOrderForApplicant(app.getApplicantId()));
		}

		ExecutorService pool = Executors.newFixedThreadPool(applicantsCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(applicantsCount);
		Set<String> allocatedIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
		List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

		for (int i = 0; i < applicantsCount; i++) {
			final long aid = applicantIds.get(i);
			final String orderId = orders.get(i).orderId();
			final String payId = "pay_multi_" + aid;
			final String sig = hmacSha256(orderId + "|" + payId, KEY_SECRET);
			pool.submit(() -> {
				try {
					start.await();
					var res = paymentService.verifyPayment(
							aid, new PaymentDtos.PaymentVerificationRequest(orderId, payId, sig));
					if (res.affiliateId() != null) {
						allocatedIds.add(res.affiliateId());
					}
				} catch (Throwable t) {
					errors.add(t);
				} finally {
					done.countDown();
				}
			});
		}

		start.countDown();
		assertTrue(done.await(20, TimeUnit.SECONDS));
		pool.shutdown();

		assertTrue(errors.isEmpty(), "Concurrent activations must complete cleanly without errors: " + errors);
		assertEquals(applicantsCount, allocatedIds.size(), "Every concurrent applicant must receive a unique Affiliate ID");
	}
}
