package com.zaylope.registration.admin;

import com.zaylope.registration.admin.AdminDtos.AdminApprovalResponse;
import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantBankDetails;
import com.zaylope.registration.applicant.ApplicantBankDetailsRepository;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.PaymentRequiredForApprovalException;
import com.zaylope.registration.document.ApplicantDocument;
import com.zaylope.registration.document.ApplicantDocumentRepository;
import com.zaylope.registration.document.ApplicantDocumentVerificationStatus;
import com.zaylope.registration.payment.PaymentOrder;
import com.zaylope.registration.payment.PaymentOrderRepository;
import com.zaylope.registration.payment.PaymentTransaction;
import com.zaylope.registration.payment.PaymentTransactionRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class AdminApprovalMySqlIntegrationTest {

	private static final String TEST_DB_NAME = "zaylope_approval_test";

	private static final AtomicInteger MOBILE_SEQ = new AtomicInteger(100000);

	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		String host = System.getenv("TEST_DB_HOST");
		if (host == null || host.isBlank()) {
			host = "127.0.0.1";
		}
		String port = System.getenv("TEST_DB_PORT");
		if (port == null || port.isBlank()) {
			port = "3306";
		}
		String baseUrl = "jdbc:mysql://" + host + ":" + port + "/?allowPublicKeyRetrieval=true&sslMode=DISABLED";
		String testDbUrl = "jdbc:mysql://" + host + ":" + port + "/" + TEST_DB_NAME + "?allowPublicKeyRetrieval=true&sslMode=DISABLED&connectionTimeZone=UTC";

		String username = System.getenv("TEST_DB_USERNAME");
		if (username == null || username.isBlank()) {
			username = System.getenv("DB_USERNAME");
		}
		if (username == null || username.isBlank()) {
			username = "root";
		}

		String password = System.getenv("TEST_DB_PASSWORD");
		if (password == null) {
			password = System.getenv("DB_PASSWORD");
		}
		if (password == null) {
			password = "";
		}

		final String effectiveUsername = username;
		final String effectivePassword = password;

		try {
			// 1. Create fresh isolated test schema
			try (Connection conn = DriverManager.getConnection(baseUrl, effectiveUsername, effectivePassword);
				 Statement stmt = conn.createStatement()) {
				stmt.execute("CREATE DATABASE IF NOT EXISTS " + TEST_DB_NAME
						+ " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
			}

			// 2. Migrate test schema with Flyway
			Flyway flyway = Flyway.configure()
					.dataSource(testDbUrl, effectiveUsername, effectivePassword)
					.locations("classpath:db/migration")
					.baselineOnMigrate(true)
					.baselineVersion("0")
					.load();
			flyway.migrate();
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize isolated MySQL test database: " + e.getMessage(), e);
		}

		registry.add("spring.datasource.url", () -> testDbUrl);
		registry.add("spring.datasource.username", () -> effectiveUsername);
		registry.add("spring.datasource.password", () -> effectivePassword);
		registry.add("spring.flyway.enabled", () -> false);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("app.bank.encryption-key", () -> "synthetic-test-only-bank-encryption-key");
		registry.add("app.documents.enabled", () -> false);
	}

	@Autowired
	private AdminApplicationService adminApplicationService;

	@Autowired
	private ApplicantRepository applicantRepository;

	@Autowired
	private ApplicantDocumentRepository documentRepository;

	@Autowired
	private ApplicantBankDetailsRepository bankDetailsRepository;

	@Autowired
	private PaymentOrderRepository paymentOrderRepository;

	@Autowired
	private PaymentTransactionRepository paymentTransactionRepository;

	@Autowired
	private AdminApprovalHistoryRepository approvalHistoryRepository;

	@Autowired
	private AffiliateMembershipRepository affiliateMembershipRepository;

	private Applicant createFullyEligibleApplicant(String name) {
		int seq = MOBILE_SEQ.incrementAndGet();
		String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		long randomDigits = (long) (Math.random() * 899999999L) + 100000000L;
		String mobile = "+919" + randomDigits;
		String email = "test." + uid + "." + seq + "@zaylope-test.com";

		Applicant applicant = new Applicant(name, mobile, email);
		Instant now = Instant.now();
		applicant.markMobileVerified(now);
		applicant.markEmailVerified(now);
		applicant.markRegistrationSubmitted(now);
		applicant.markPaymentPaid();
		applicant = applicantRepository.saveAndFlush(applicant);

		Long applicantId = applicant.getApplicantId();

		// Add payment order and verified ₹599 payment transaction
		String orderIdStr = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
		PaymentOrder order = new PaymentOrder(applicantId, orderIdStr, 59900L, "INR", "rcpt_" + uid);
		order = paymentOrderRepository.saveAndFlush(order);

		PaymentTransaction tx = new PaymentTransaction(
				order.getOrderId(),
				applicantId,
				"pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14),
				orderIdStr,
				59900L,
				"INR",
				"upi",
				"CAPTURED",
				"test_sig_" + uid,
				true
		);
		paymentTransactionRepository.saveAndFlush(tx);

		// Add approved PAN document
		ApplicantDocument doc = new ApplicantDocument(
				UUID.randomUUID().toString(),
				applicant,
				"PAN Card",
				"pan-storage-" + uid,
				"pan.jpg",
				"image/jpeg",
				1024L,
				now,
				now,
				"1.0"
		);
		doc.updateVerificationStatus(ApplicantDocumentVerificationStatus.APPROVED);
		documentRepository.saveAndFlush(doc);

		// Add valid encrypted bank details
		ApplicantBankDetails bank = new ApplicantBankDetails(
				applicantId,
				name,
				"HDFC Bank",
				"mock_encrypted_ciphertext_blob",
				"9876",
				"••••••••9876",
				"HDFC0001234"
		);
		bankDetailsRepository.saveAndFlush(bank);

		return applicant;
	}

	@Test
	@DisplayName("Concurrent approvals for the same applicant must be idempotent with exactly one approval record")
	void testConcurrentApprovalsForSameApplicant() throws Exception {
		Applicant applicant = createFullyEligibleApplicant("Concurrent Single User");
		Long applicantId = applicant.getApplicantId();

		int threads = 4;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch doneLatch = new CountDownLatch(threads);

		List<Future<AdminApprovalResponse>> futures = new ArrayList<>();

		for (int i = 0; i < threads; i++) {
			final String adminName = "admin_thread_" + i;
			futures.add(executor.submit(() -> {
				try {
					startLatch.await();
					return adminApplicationService.approveApplicant(applicantId, adminName, "Concurrent test approval");
				} finally {
					doneLatch.countDown();
				}
			}));
		}

		startLatch.countDown();
		boolean allFinished = doneLatch.await(10, TimeUnit.SECONDS);
		executor.shutdown();

		List<AdminApprovalResponse> responses = new ArrayList<>();
		for (Future<AdminApprovalResponse> f : futures) {
			responses.add(f.get());
		}

		// Verify every caller received identical Affiliate ID and APPROVED status
		String firstAffiliateId = responses.get(0).affiliateId();
		String firstReferralCode = responses.get(0).referralCode();
		assertNotNull(firstAffiliateId);
		assertTrue(firstAffiliateId.startsWith("ZP-"));

		for (AdminApprovalResponse resp : responses) {
			assertEquals("APPROVED", resp.approvalStatus());
			assertEquals(firstAffiliateId, resp.affiliateId(), "All concurrent callers must receive identical Affiliate ID");
			assertEquals(firstReferralCode, resp.referralCode(), "All concurrent callers must receive identical referral code");
		}

		// Verify database state: exactly one affiliate membership and one approval history entry
		var memberships = affiliateMembershipRepository.findByApplicantId(applicantId);
		assertTrue(memberships.isPresent());
		assertEquals(firstAffiliateId, memberships.get().getAffiliateId());

		var history = approvalHistoryRepository.findByApplicantIdOrderByOccurredAtDesc(applicantId);
		assertEquals(1, history.size(), "Only exactly one approval history record must be created");
	}

	@Test
	@DisplayName("Concurrent approvals across multiple applicants must guarantee Affiliate ID uniqueness")
	void testConcurrentApprovalsAcrossDifferentApplicants() throws Exception {
		int applicantCount = 5;
		List<Applicant> applicants = new ArrayList<>();
		for (int i = 0; i < applicantCount; i++) {
			applicants.add(createFullyEligibleApplicant("Multi Applicant " + i));
		}

		ExecutorService executor = Executors.newFixedThreadPool(applicantCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		List<Future<AdminApprovalResponse>> futures = new ArrayList<>();

		for (Applicant app : applicants) {
			Long appId = app.getApplicantId();
			futures.add(executor.submit(() -> {
				startLatch.await();
				return adminApplicationService.approveApplicant(appId, "superadmin", "Batch concurrent approval");
			}));
		}

		startLatch.countDown();
		executor.shutdown();
		executor.awaitTermination(15, TimeUnit.SECONDS);

		Set<String> affiliateIds = new HashSet<>();
		Set<String> referralCodes = new HashSet<>();

		for (Future<AdminApprovalResponse> f : futures) {
			AdminApprovalResponse resp = f.get();
			assertEquals("APPROVED", resp.approvalStatus());
			assertNotNull(resp.affiliateId());
			assertTrue(resp.affiliateId().matches("^ZP-\\d{6}-\\d{3}$"), "Affiliate ID must match format ZP-YYMMDD-SSS: " + resp.affiliateId());
			affiliateIds.add(resp.affiliateId());
			referralCodes.add(resp.referralCode());
		}

		// Uniqueness assertions
		assertEquals(applicantCount, affiliateIds.size(), "Every approved applicant must have a strictly unique Affiliate ID");
		assertEquals(applicantCount, referralCodes.size(), "Every approved applicant must have a strictly unique referral code");
	}

	@Test
	@DisplayName("Transaction rollback: Unfulfilled conditions rollback completely without orphan data")
	void testApprovalTransactionRollbackOnFailure() {
		Applicant applicant = createFullyEligibleApplicant("Rollback User");
		Long applicantId = applicant.getApplicantId();

		// Remove the payment transaction so Gate 4 fails
		paymentTransactionRepository.deleteAll(paymentTransactionRepository.findByApplicantIdOrderByCreatedAtDesc(applicantId));

		assertThrows(PaymentRequiredForApprovalException.class, () ->
				adminApplicationService.approveApplicant(applicantId, "admin", "Should fail"));

		// Verify database rollback: applicant remains unchanged, no history, no membership
		Applicant reloaded = applicantRepository.findById(applicantId).orElseThrow();
		assertNull(reloaded.getAffiliateId(), "Affiliate ID must remain null on transaction rollback");
		assertEquals("PENDING", reloaded.getApprovalStatus(), "Approval status must remain PENDING on rollback");
		assertNull(reloaded.getApprovedAt(), "Approved timestamp must remain null");

		assertTrue(affiliateMembershipRepository.findByApplicantId(applicantId).isEmpty(),
				"No affiliate membership should be persisted on rollback");
		assertTrue(approvalHistoryRepository.findByApplicantIdOrderByOccurredAtDesc(applicantId).isEmpty(),
				"No approval history should be persisted on rollback");
	}
}
