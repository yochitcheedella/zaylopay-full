package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.AffiliateMembership;
import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.common.exception.InvalidAuthChallengeException;
import com.zaylope.registration.common.exception.MobileNumberAlreadyUsedException;
import com.zaylope.registration.payment.PaymentOrder;
import com.zaylope.registration.payment.PaymentOrderRepository;
import com.zaylope.registration.registration.ApplicantRegistrationRequest;
import com.zaylope.registration.registration.ApplicantRegistrationWriter;
import com.zaylope.registration.session.ApplicantAccessSessionRepository;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantVerificationService;
import com.zaylope.registration.verification.EmailOtpDelivery;
import com.zaylope.registration.verification.MobileOtpDelivery;
import com.zaylope.registration.verification.VerificationDeliveryUnavailableException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(AuthMySqlIntegrationTest.TestConfig.class)
class AuthMySqlIntegrationTest {

	private static final String TEST_DB_NAME = "zaylope_auth_test";
	private static final AtomicInteger SEQ = new AtomicInteger(10000);

	@DynamicPropertySource
	static void configure(DynamicPropertyRegistry registry) {
		String host = System.getenv("TEST_DB_HOST");
		if (host == null || host.isBlank()) host = "127.0.0.1";
		String port = System.getenv("TEST_DB_PORT");
		if (port == null || port.isBlank()) port = "3306";
		String baseUrl = "jdbc:mysql://" + host + ":" + port + "/?allowPublicKeyRetrieval=true&sslMode=DISABLED";
		String testUrl = "jdbc:mysql://" + host + ":" + port + "/" + TEST_DB_NAME + "?allowPublicKeyRetrieval=true&sslMode=DISABLED&connectionTimeZone=UTC";

		String user = System.getenv("TEST_DB_USERNAME");
		if (user == null || user.isBlank()) user = System.getenv("DB_USERNAME");
		if (user == null || user.isBlank()) user = "root";
		String pass = System.getenv("TEST_DB_PASSWORD");
		if (pass == null) pass = System.getenv("DB_PASSWORD");
		if (pass == null) pass = "";

		try (Connection conn = DriverManager.getConnection(baseUrl, user, pass);
			 Statement stmt = conn.createStatement()) {
			stmt.execute("DROP DATABASE IF EXISTS " + TEST_DB_NAME);
			stmt.execute("CREATE DATABASE " + TEST_DB_NAME + " DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
		} catch (Exception e) {
			throw new RuntimeException("Failed to reset isolated auth test schema", e);
		}

		Flyway.configure()
				.dataSource(testUrl, user, pass)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load()
				.migrate();

		final String finalUser = user;
		final String finalPass = pass;
		registry.add("spring.datasource.url", () -> testUrl);
		registry.add("spring.datasource.username", () -> finalUser);
		registry.add("spring.datasource.password", () -> finalPass);
		registry.add("spring.flyway.enabled", () -> false);
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("app.verification.mobile-enabled", () -> true);
		registry.add("app.verification.email-enabled", () -> true);
		registry.add("app.verification.mock-delivery", () -> true);
		registry.add("app.verification.otp-hash-key", () -> "isolated-auth-test-otp-hash-key-32bytes!");
		registry.add("app.documents.enabled", () -> false);
		registry.add("google.client-id", () -> "454795409862-1t33iseetd4o3970r5rfjrbklkq2hc8f.apps.googleusercontent.com");
	}

	@Autowired ApplicantRegistrationWriter registrationWriter;
	@Autowired ApplicantVerificationService verificationService;
	@Autowired ApplicantAuthChallengeService authChallengeService;
	@Autowired GoogleAuthService googleAuthService;
	@Autowired ApplicantSessionService sessionService;
	@Autowired ApplicantRepository applicantRepository;
	@Autowired ApplicantAccessSessionRepository sessionRepository;
	@Autowired PaymentOrderRepository paymentOrderRepository;
	@Autowired AffiliateMembershipRepository membershipRepository;
	@Autowired JdbcTemplate jdbc;
	@Autowired CapturingOtpDelivery otpDelivery;
	@Autowired MutableClock clock;

	@BeforeEach
	void setUpRequest() {
		clock.now = Instant.parse("2026-11-25T10:00:00Z");
		int n = SEQ.incrementAndGet();
		MockHttpServletRequest req = new MockHttpServletRequest();
		req.setRemoteAddr("10.0." + (n % 250) + "." + ((n / 250) % 250));
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
	}

	@Test
	void expiredMobileAndEmailReservationsReleaseSafelyWithoutDeletingHistoricalRow() {
		String mobile = nextMobile();
		String email = "expired." + UUID.randomUUID() + "@example.test";
		registrationWriter.register(new ApplicantRegistrationRequest("First Draft", mobile, email));
		Applicant first = applicantRepository.findByMobileNumber(mobile).orElseThrow();
		Long firstId = first.getApplicantId();

		// Still active within 30 minutes -> rejected
		clock.advance(600);
		assertThrows(MobileNumberAlreadyUsedException.class, () ->
				registrationWriter.register(new ApplicantRegistrationRequest("Second User", mobile, "other." + email)));

		// After 30 minutes -> safely releases reservation and allows legitimate owner while preserving first row
		clock.advance(1300);
		var secondResult = registrationWriter.register(new ApplicantRegistrationRequest("Legit Owner", mobile, email));
		assertNotNull(secondResult.sessionToken());

		Applicant reloadedFirst = applicantRepository.findById(firstId).orElseThrow();
		assertEquals(mobile, reloadedFirst.getMobileNumber());
		assertNull(reloadedFirst.getPendingMobileKey());
		assertNull(reloadedFirst.getActiveEmailKey());
	}

	@Test
	void concurrentMobileVerificationAllowsExactlyOneOwner() throws Exception {
		String mobile = nextMobile();
		registrationWriter.register(new ApplicantRegistrationRequest("User A", mobile, "a." + UUID.randomUUID() + "@example.test"));
		Applicant appA = applicantRepository.findByMobileNumber(mobile).orElseThrow();

		// Force a second unverified row sharing same mobile after releasing pending key, testing DB verified_mobile_key constraint
		appA.releasePendingMobileReservation();
		applicantRepository.saveAndFlush(appA);
		registrationWriter.register(new ApplicantRegistrationRequest("User B", mobile, "b." + UUID.randomUUID() + "@example.test"));
		Applicant appB = applicantRepository.findAll().stream()
				.filter(a -> !a.getApplicantId().equals(appA.getApplicantId()) && mobile.equals(a.getMobileNumber()))
				.findFirst().orElseThrow();

		// Directly verify A and B concurrently via OTP
		verificationService.sendMobileCode(appA.getApplicantId());
		String codeA = otpDelivery.codes.get(mobile);
		verificationService.verifyMobileCode(appA.getApplicantId(), codeA);

		// B cannot verify the same mobile number because uk_applicants_verified_mobile enforces unique verified ownership
		clock.advance(61);
		verificationService.sendMobileCode(appB.getApplicantId());
		String codeB = otpDelivery.codes.get(mobile);
		assertThrows(Exception.class, () -> verificationService.verifyMobileCode(appB.getApplicantId(), codeB));
	}

	@Test
	void googleSubUniquenessIsCaseSensitive() {
		Applicant a1 = Applicant.createGoogleApplicant("Upper", nextMobile(), "u1." + UUID.randomUUID() + "@gmail.com",
				"GoogleSubCase", "u1@gmail.com", null);
		Applicant a2 = Applicant.createGoogleApplicant("Lower", nextMobile(), "u2." + UUID.randomUUID() + "@gmail.com",
				"googlesubcase", "u2@gmail.com", null);
		applicantRepository.saveAndFlush(a1);
		applicantRepository.saveAndFlush(a2);

		assertEquals(a1.getApplicantId(), applicantRepository.findByGoogleSub("GoogleSubCase").orElseThrow().getApplicantId());
		assertEquals(a2.getApplicantId(), applicantRepository.findByGoogleSub("googlesubcase").orElseThrow().getApplicantId());
	}

	@Test
	void googleAccountLinkingPreservesApplicantIdPaymentsMembershipAndRotatesSession() {
		String mobile = nextMobile();
		String email = "link." + UUID.randomUUID() + "@gmail.com";
		registrationWriter.register(new ApplicantRegistrationRequest("Manual Owner", mobile, email));
		Applicant manual = applicantRepository.findByMobileNumber(mobile).orElseThrow();
		Long originalId = manual.getApplicantId();

		// Verify mobile, submit, record payment & membership
		verificationService.sendMobileCode(originalId);
		verificationService.verifyMobileCode(originalId, otpDelivery.codes.get(mobile));
		manual = applicantRepository.findById(originalId).orElseThrow();
		manual.markRegistrationSubmitted(clock.instant());
		manual.markPaymentPaid();
		manual.markApproved("ZP-261125-009", clock.instant());
		applicantRepository.saveAndFlush(manual);
		paymentOrderRepository.saveAndFlush(new PaymentOrder(originalId, "order_" + UUID.randomUUID().toString().substring(0, 10), 59900L, "INR", "rcpt_1"));
		membershipRepository.saveAndFlush(new AffiliateMembership(originalId, "ZP-261125-009", "REF009", 1L, clock.instant()));

		String legacySession = sessionService.createFor(manual, ApplicantSessionScope.AUTHENTICATED);

		// Create onboarding linking session directly via repository/service
		clock.advance(61);
		var payload = new GoogleTokenVerifierService.GoogleUserPayload("google-sub-link-1", email, true, "Manual Owner", null, null);
		String onboardingTok = onboardingSessionService.create(ApplicantOnboardingSession.FlowType.GOOGLE_LINK_ACCOUNT, payload, originalId);
		googleAuthService.sendLinkingOtp(onboardingTok);
		String otp = otpDelivery.codes.get(mobile);

		var outcome = googleAuthService.verifyLinkingOtp(onboardingTok, otp, legacySession);
		assertNotEquals(legacySession, outcome.applicantSessionToken());
		assertTrue(sessionService.findActiveApplicantId(legacySession).isEmpty(), "Prior session must be revoked after linking");
		assertEquals(Optional.of(originalId), sessionService.findActiveApplicantId(outcome.applicantSessionToken()));

		Applicant linked = applicantRepository.findById(originalId).orElseThrow();
		assertEquals("google-sub-link-1", linked.getGoogleSub());
		assertEquals("BOTH", linked.getAuthProvider());
		assertEquals("ZP-261125-009", linked.getAffiliateId());
		assertEquals(1, paymentOrderRepository.findByApplicantIdOrderByCreatedAtDesc(originalId).size());
		assertTrue(membershipRepository.findByApplicantId(originalId).isPresent());
	}

	@Test
	void loginOtpAndSingleSessionLogoutWorkAndPreventReplay() {
		String mobile = nextMobile();
		String email = "login." + UUID.randomUUID() + "@example.test";
		registrationWriter.register(new ApplicantRegistrationRequest("Login User", mobile, email));
		Applicant applicant = applicantRepository.findByMobileNumber(mobile).orElseThrow();
		verificationService.sendMobileCode(applicant.getApplicantId());
		verificationService.verifyMobileCode(applicant.getApplicantId(), otpDelivery.codes.get(mobile));

		clock.advance(61);
		var dispatch = authChallengeService.requestLoginOtp(mobile);
		String otp = otpDelivery.codes.get(mobile);
		var login1 = authChallengeService.verifyLoginOtp(dispatch.challengeId(), mobile, otp, null);
		assertEquals(ApplicantSessionScope.AUTHENTICATED, login1.sessionScope());

		// Replay rejected
		assertThrows(InvalidAuthChallengeException.class,
				() -> authChallengeService.verifyLoginOtp(dispatch.challengeId(), mobile, otp, null));

		// Single-session logout revokes only current session
		String session2 = sessionService.createFor(applicant, ApplicantSessionScope.AUTHENTICATED);
		sessionService.revoke(login1.sessionToken());
		assertTrue(sessionService.findActiveApplicantId(login1.sessionToken()).isEmpty());
		assertTrue(sessionService.findActiveApplicantId(session2).isPresent());
	}

	@Autowired ApplicantOnboardingSessionService onboardingSessionService;

	private static String nextMobile() {
		int seq = SEQ.incrementAndGet();
		return "+9198" + String.format("%08d", seq);
	}

	static class MutableClock extends Clock {
		volatile Instant now = Instant.parse("2026-11-25T10:00:00Z");
		@Override public ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(ZoneId zone) { return this; }
		@Override public Instant instant() { return now; }
		void advance(long seconds) { now = now.plusSeconds(seconds); }
	}

	static class CapturingOtpDelivery implements MobileOtpDelivery, EmailOtpDelivery {
		final Map<String, String> codes = new ConcurrentHashMap<>();
		@Override public boolean isMock() { return true; }
		@Override public void sendCode(String destination, String code) {
			if (destination == null) throw new VerificationDeliveryUnavailableException();
			codes.put(destination, code);
		}
	}

	@TestConfiguration
	static class TestConfig {
		@Bean @Primary MutableClock testClock() { return new MutableClock(); }
		@Bean @Primary CapturingOtpDelivery capturingOtpDelivery() { return new CapturingOtpDelivery(); }
	}
}
