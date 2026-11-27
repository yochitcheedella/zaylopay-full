package com.zaylope.registration.verification;

import com.zaylope.registration.applicant.*;
import com.zaylope.registration.common.validation.EmailDnsResolver;
import java.net.*;
import java.net.http.*;
import java.sql.DriverManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP/JPA/MySQL on the harness-owned port/schema only. No destructive reset or real delivery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(IdentityValidationMySqlTest.TestBeans.class)
@EnabledIfEnvironmentVariable(named = "OTP_MYSQL_TEST", matches = "true")
class IdentityValidationMySqlTest {
    private static final String SCHEMA = "zaylope_identity_" + UUID.randomUUID().toString().replace("-", "");
    private static final String BASE_URL = "jdbc:mysql://127.0.0.1:3308/zaylope_otp_test?allowPublicKeyRetrieval=true&sslMode=DISABLED&connectionTimeZone=UTC";
    private static final String URL = BASE_URL.replace("/zaylope_otp_test?", "/" + SCHEMA + "?");
    private static final AtomicInteger SEQUENCE = new AtomicInteger((int) (System.currentTimeMillis() % 800_000_000));
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) throws Exception {
        String password = System.getenv("OTP_TEST_DB_PASSWORD");
        if (password == null || password.isBlank()) throw new IllegalStateException("Use the isolated test harness");
        try (var connection = DriverManager.getConnection(BASE_URL, "root", password)) {
            assertEquals("zaylope_otp_test", connection.getCatalog());
            // A fresh additive schema isolates durable IP limits between runs without deleting prior fixture records.
            try (var statement = connection.createStatement()) { statement.execute("CREATE DATABASE " + SCHEMA); }
        }
        org.flywaydb.core.Flyway.configure().dataSource(URL, "root", password)
                .locations("classpath:db/migration").load().migrate();
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> "root");
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.enabled", () -> false);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        properties.add("app.verification.mobile-enabled", () -> true);
        properties.add("app.verification.email-enabled", () -> true);
        properties.add("app.verification.mock-delivery", () -> true);
        properties.add("app.verification.otp-hash-key", () -> "isolated-fixture-only-strong-key-at-least-32-bytes");
        properties.add("app.email-validation.dns-enabled", () -> true);
        properties.add("app.cors.allowed-origin", () -> "http://localhost:5180");
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired CapturingDelivery delivery;
    @Autowired ApplicantRepository applicants;
    @Autowired FixtureClock clock;
    @Autowired com.zaylope.registration.auth.ApplicantOnboardingSessionService onboarding;
    @Autowired com.zaylope.registration.auth.GoogleAuthService google;
    @Autowired ControlledGoogleVerifier googleVerifier;
    @Autowired javax.sql.DataSource datasource;
    @Autowired com.zaylope.registration.affiliate.AffiliateMembershipRepository memberships;
    @Autowired com.zaylope.registration.payment.PaymentOrderRepository orders;
    @Autowired com.zaylope.registration.payment.PaymentTransactionRepository payments;

    @Autowired com.zaylope.registration.auth.ApplicantAuthChallengeService loginService;
    private void explicitMode(boolean enabled) { org.springframework.test.util.ReflectionTestUtils.setField(loginService, "explicitLoginOutcomes", enabled); }
    @AfterEach void restorePrivacyDefault() { explicitMode(false); }
    @Test void explicitEligibilityUsesRealDatabase() throws Exception {
        explicitMode(true);
        int sends = delivery.count.get();
        int records = jdbc.queryForObject("SELECT COUNT(*) FROM applicants", Integer.class);
        int sessions = jdbc.queryForObject("SELECT COUNT(*) FROM applicant_access_sessions", Integer.class);
        int challenges = jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges", Integer.class);
        for (String id : List.of("Sai@kin.com", mobile())) {
            var result = tree(call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", id))));
            assertEquals("ACCOUNT_NOT_FOUND", result.path("outcome").asString());
            assertFalse(result.path("accepted").asBoolean());
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/explicit-unknown-api.json"), result.toPrettyString());
            assertTrue(result.path("challengeId").isNull());
            assertEquals(0, result.path("expiresInSeconds").asInt());
        }
        assertEquals(sends, delivery.count.get());
        assertEquals(records, jdbc.queryForObject("SELECT COUNT(*) FROM applicants", Integer.class));
        assertEquals(sessions, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_access_sessions", Integer.class));
        assertEquals(challenges, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges", Integer.class));
        Applicant verified = verifiedApplicant(email("gmail.com"));
        verified.markMobileVerified(clock.instant()); applicants.saveAndFlush(verified);
        for (String id : List.of(verified.getEmail(), verified.getMobileNumber())) { clock.advance(3601);
            assertEquals("CHALLENGE_INITIATED", tree(call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", id)))).path("outcome").asString());
        }
        Applicant draft = applicants.saveAndFlush(new Applicant("QA Unverified", mobile(), email("gmail.com")));
        assertEquals("CONTACT_VERIFICATION_REQUIRED", tree(call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", draft.getEmail())))).path("outcome").asString());
        verified.markRejected("Fixture restriction", clock.instant()); applicants.saveAndFlush(verified); clock.advance(3601);
        assertEquals("ACCOUNT_UNAVAILABLE", tree(call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", verified.getEmail())))).path("outcome").asString());
    }
    @BeforeEach
    void isolateDurableBudgetsWithoutDeletingRows() { clock.advance(3601); }

    @Test
    void healthPolicyAndActualIsolatedSchemaVersion() throws Exception {
        var client = client();
        assertEquals("UP", tree(call(client, "GET", "/actuator/health", null)).path("status").asString());
        var policy = tree(call(client, "GET", "/api/v1/validation/email-policy", null));
        assertTrue(policy.path("allowlistEnabled").asBoolean());
        assertEquals(10, policy.path("allowedDomains").size());
        assertTrue(policy.path("disposableDomains").size() > 1000);
        assertEquals("13", jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1", String.class));
    }

    @Test
    void actualOwnershipLoginLookupNoUnknownDeliverySessionRotationAndLogout() throws Exception {
        String email = email("gmail.com");
        var owner = client();
        register(owner, email);
        long id = id(email);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicants WHERE applicant_id=? AND email_verified_at IS NOT NULL", Integer.class, id));
        assertEquals(200, call(owner, "POST", "/api/v1/applicants/me/verification/email/send", "{}").statusCode());
        String ownershipCode = delivery.await(email);
        assertEquals(200, call(owner, "POST", "/api/v1/applicants/me/verification/email/verify", json.writeValueAsString(Map.of("code", ownershipCode))).statusCode());
        assertTrue(applicants.findById(id).orElseThrow().isTrustedEmailVerified());
        assertFalse(applicants.findById(id).orElseThrow().isMobileVerified());
        var anonymous = client();
        clock.advance(61); // Registration and login correctly share the destination cooldown.
        int deliveries = delivery.count.get();
        String unknownEmail = email("gmail.com");
        var unknown = tree(call(anonymous, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", unknownEmail))));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, unknown.path("challengeId").asString()));
        assertEquals(deliveries, delivery.count.get());
        assertFalse(tree(call(anonymous, "GET", "/api/v1/auth/session", null)).path("authenticated").asBoolean());
        var issued = tree(call(anonymous, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", email))));
        String loginCode = delivery.await(email);
        assertEquals(unknown.path("message").asString(), issued.path("message").asString());
        assertEquals(unknown.path("expiresInSeconds").asLong(), issued.path("expiresInSeconds").asLong());
        assertEquals(unknown.path("resendAfterSeconds").asLong(), issued.path("resendAfterSeconds").asLong());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, issued.path("challengeId").asString()));
        String challenge = issued.path("challengeId").asString();
        assertEquals(400, call(anonymous, "POST", "/api/v1/auth/login/otp/verify", verifyBody(challenge, unknownEmail, loginCode)).statusCode());
        String wrongCode = loginCode.equals("000000") ? "111111" : "000000";
        assertEquals(400, call(anonymous, "POST", "/api/v1/auth/login/otp/verify", verifyBody(challenge, email, wrongCode)).statusCode());
        var verified = call(anonymous, "POST", "/api/v1/auth/login/otp/verify", verifyBody(challenge, email, loginCode));
        assertEquals(200, verified.statusCode());
        assertEquals("QA Identity Applicant", tree(verified).path("fullName").asString());
        assertTrue(verified.headers().allValues("Set-Cookie").stream().anyMatch(cookie -> cookie.contains("HttpOnly")));
        var session = tree(call(anonymous, "GET", "/api/v1/auth/session", null));
        assertTrue(session.path("authenticated").asBoolean());
        assertTrue(session.path("identityAuthenticated").asBoolean());
        assertEquals("PAYMENT", session.path("eligibleStep").asString());
        assertEquals("NOT_STARTED", session.path("membershipStatus").asString());
        assertEquals(400, call(anonymous, "POST", "/api/v1/auth/login/otp/verify", verifyBody(challenge, email, loginCode)).statusCode());
        assertEquals(403, call(anonymous, "GET", "/api/v1/applicants/me/membership", null).statusCode());
        assertEquals(204, call(anonymous, "POST", "/api/v1/auth/logout", "{}").statusCode());
        assertFalse(tree(call(anonymous, "GET", "/api/v1/auth/session", null)).path("authenticated").asBoolean());
        assertEquals(id, id(email));
        assertNull(applicants.findById(id).orElseThrow().getAffiliateId());
    }

    @Test
    void existingVerifiedCustomDomainKeepsLoginWhileNewRegistrationIsDenied() throws Exception {
        String email = "CaseSensitive+" + UUID.randomUUID() + "@corp.example.dev";
        Applicant fixture = new Applicant("QA Custom Member", mobile(), email);
        fixture.markEmailVerified(Instant.now(), Applicant.EMAIL_METHOD_OTP);
        fixture = applicants.saveAndFlush(fixture);
        var client = client();
        var issued = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", email))));
        String code = delivery.await(email);
        assertEquals(200, call(client, "POST", "/api/v1/auth/login/otp/verify", verifyBody(issued.path("challengeId").asString(), email, code)).statusCode());
        assertEquals(fixture.getApplicantId(), id(email));
        var denied = call(client(), "POST", "/api/v1/applicants/register", registrationBody(email("corp.example.dev"), mobile()));
        assertEquals(400, denied.statusCode());
    }

    @Test
    void draftCorrectionKeepsApplicantAndInvalidatesOldCodeAndRejectsUnsupportedAndUnchangedContacts() throws Exception {
        String email = email("gmail.com");
        var client = client();
        register(client, email);
        long id = id(email);
        assertEquals(200, call(client, "POST", "/api/v1/applicants/me/verification/email/send", "{}").statusCode());
        String old = delivery.await(email);
        assertEquals(400, call(client, "PUT", "/api/v1/applicants/me/verification/email", json.writeValueAsString(Map.of("email", email("kin.com")))).statusCode());
        assertEquals(400, call(client, "PUT", "/api/v1/applicants/me/verification/email", json.writeValueAsString(Map.of("email", email))).statusCode());
        String replacement = email("yahoo.com");
        assertEquals(200, call(client, "PUT", "/api/v1/applicants/me/verification/email", json.writeValueAsString(Map.of("email", replacement))).statusCode());
        assertEquals(id, id(replacement));
        assertFalse(applicants.findById(id).orElseThrow().isEmailVerified());
        assertNotEquals(200, call(client, "POST", "/api/v1/applicants/me/verification/email/verify", json.writeValueAsString(Map.of("code", old))).statusCode());
    }

    @Test
    void concurrentDraftContactUpdatesCannotClaimSameEmail() throws Exception {
        var first = client(); var second = client();
        register(first, email("gmail.com")); register(second, email("gmail.com"));
        String replacement = email("yahoo.com");
        String body = json.writeValueAsString(Map.of("email", replacement));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> call(first, "PUT", "/api/v1/applicants/me/verification/email", body).statusCode());
            var b = executor.submit(() -> call(second, "PUT", "/api/v1/applicants/me/verification/email", body).statusCode());
            var statuses = new ArrayList<>(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
            Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM applicants WHERE active_email_key=?", Integer.class, replacement));
    }

    @Test
    void verifiedMobileLoginDoesNotVerifyEmailAndUnknownMobileGetsNoChallenge() throws Exception {
        Applicant fixture = new Applicant("QA Mobile Member", mobile(), email("gmail.com"));
        fixture.markMobileVerified(clock.instant());
        fixture = applicants.saveAndFlush(fixture);
        var client = client();
        int before = delivery.count.get();
        String unknownMobile = mobile();
        var unknown = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", unknownMobile))));
        assertEquals(before, delivery.count.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, unknown.path("challengeId").asString()));
        var issued = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", fixture.getMobileNumber()))));
        String code = delivery.await(fixture.getMobileNumber());
        assertEquals(200, call(client, "POST", "/api/v1/auth/login/otp/verify", verifyBody(issued.path("challengeId").asString(), fixture.getMobileNumber(), code)).statusCode());
        var session = tree(call(client, "GET", "/api/v1/auth/session", null));
        assertTrue(session.path("mobileVerified").asBoolean());
        assertFalse(session.path("emailVerified").asBoolean());
    }

    @Test
    void controlledGoogleOnboardingUsesAdmissionPolicyAndIndependentMobileOwnership() {
        String address = email("gmail.com");
        var payload = new com.zaylope.registration.auth.GoogleTokenVerifierService.GoogleUserPayload(
                "qa-google-" + UUID.randomUUID(), address, true, "QA Google Applicant", null);
        String token = onboarding.create(com.zaylope.registration.auth.ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE, payload, null);
        var outcome = google.completeProfile(token, "QA Google Applicant", mobile(), null);
        assertEquals("AUTHENTICATED", outcome.response().status());
        Applicant applicant = applicants.findById(id(address)).orElseThrow();
        assertTrue(applicant.isTrustedEmailVerified());
        assertFalse(applicant.isMobileVerified());
        assertEquals(Applicant.EMAIL_METHOD_GOOGLE, applicant.getEmailVerificationMethod());
        String blocked = email("kin.com");
        var blockedPayload = new com.zaylope.registration.auth.GoogleTokenVerifierService.GoogleUserPayload(
                "qa-google-" + UUID.randomUUID(), blocked, true, "QA Google Applicant", null, "kin.com");
        String blockedToken = onboarding.create(com.zaylope.registration.auth.ApplicantOnboardingSession.FlowType.GOOGLE_COMPLETE_PROFILE, blockedPayload, null);
        assertThrows(com.zaylope.registration.common.exception.InvalidRegistrationInputException.class,
                () -> google.completeProfile(blockedToken, "QA Google Applicant", mobile(), null));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicants WHERE email_address=?", Integer.class, blocked));
    }

    @Test
    void providerFailureIsPersistedUnusableAndVisibleOnlyInOwnedVerificationStatus() throws Exception {
        String address = email("gmail.com");
        var client = client(); register(client, address);
        long id = id(address);
        delivery.failedDestinations.add(address);
        assertEquals(200, call(client, "POST", "/api/v1/applicants/me/verification/email/send", "{}").statusCode());
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String state;
        do {
            state = jdbc.queryForObject("SELECT challenge_status FROM applicant_verification_codes WHERE applicant_id=? AND verification_channel='EMAIL'", String.class, id);
            if ("DELIVERY_FAILED".equals(state)) break;
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);
        assertEquals("DELIVERY_FAILED", state);
        var status = tree(call(client, "GET", "/api/v1/applicants/me/verification/status", null));
        assertEquals("DELIVERY_FAILED", status.path("email").path("challengeStatus").asString());
        assertFalse(status.path("email").path("verified").asBoolean());
        assertNotEquals(200, call(client, "POST", "/api/v1/applicants/me/verification/email/verify", "{\"code\":\"000000\"}").statusCode());
    }

    @Test
    void schemaMetadataAuditIsReadOnlyAndIncludesIdentityConstraintsAndGoogleFields() throws Exception {
        try (var connection = datasource.getConnection()) {
            var report = com.zaylope.registration.auth.diagnostics.AuthenticationSchemaAudit.inspect(connection, SCHEMA);
            assertEquals(true, report.get("readOnly"));
            assertEquals(List.of(), report.get("missingTables"));
            String metadata = json.writerWithDefaultPrettyPrinter().writeValueAsString(report);
            assertTrue(metadata.contains("uk_applicants_verified_email"));
            assertTrue(metadata.contains("email_case_sensitive"));
            assertTrue(metadata.contains("google_sub"));
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/auth-isolated-schema-audit.json"), metadata);
        }
    }

    @Test
    void reportedUnknownIdentifiersCreateNoApplicantChallengeDeliveryOrSessionAndHaveScopedCooldowns() throws Exception {
        var client = client();
        int before = delivery.count.get();
        long applicantsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM applicants", Long.class);
        for (String identifier : List.of("gopi@gm.com", "sai@gmi.com", "sai@kin.com", mobile())) {
            var response = call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", identifier)));
            assertEquals(200, response.statusCode());
            String challenge = tree(response).path("challengeId").asString();
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, challenge));
            var limited = call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", identifier)));
            assertEquals(429, limited.statusCode());
            assertEquals("IDENTIFIER", tree(limited).path("rateLimitScope").asString());
            assertEquals(60, tree(limited).path("retryAfterSeconds").asInt());
            assertTrue(tree(limited).has("retryAvailableAt"));
        }
        assertEquals(400, call(client, "POST", "/api/v1/auth/login/otp/request", "{\"identifier\":\"sai18@gmial.ocm\"}").statusCode());
        assertEquals(before, delivery.count.get());
        assertEquals(applicantsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM applicants", Long.class));
        assertFalse(tree(call(client, "GET", "/api/v1/auth/session", null)).path("authenticated").asBoolean());
    }

    @Test
    void unverifiedDraftsCannotUseNormalLoginButExplicitRecoveryProvesEmailOwnership() throws Exception {
        String address = email("gmail.com");
        register(client(), address);
        var client = client();
        int before = delivery.count.get();
        var normal = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", address))));
        assertEquals(before, delivery.count.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, normal.path("challengeId").asString()));
        assertEquals(429, call(client, "POST", "/api/v1/auth/recovery/otp/request", json.writeValueAsString(Map.of("identifier", address))).statusCode());
        clock.advance(61);
        var recovery = tree(call(client, "POST", "/api/v1/auth/recovery/otp/request", json.writeValueAsString(Map.of("identifier", address))));
        String code = delivery.await(address);
        assertEquals(400, call(client, "POST", "/api/v1/auth/login/otp/verify", verifyBody(recovery.path("challengeId").asString(), address, code)).statusCode());
        assertFalse(tree(call(client, "GET", "/api/v1/auth/session", null)).path("authenticated").asBoolean());
        var verified = call(client, "POST", "/api/v1/auth/recovery/otp/verify", verifyBody(recovery.path("challengeId").asString(), address, code));
        assertEquals(200, verified.statusCode());
        assertTrue(tree(verified).path("draftRecovered").asBoolean());
        assertTrue(applicants.findById(id(address)).orElseThrow().isTrustedEmailVerified());
        assertEquals("PAYMENT", tree(call(client, "GET", "/api/v1/auth/session", null)).path("eligibleStep").asString());
    }

    @Test
    void unverifiedMobileRestrictedAccountsAndWrongLocalCaseReceiveNoLoginCode() throws Exception {
        Applicant unverified = new Applicant("QA Unverified", mobile(), email("gmail.com"));
        applicants.saveAndFlush(unverified);
        Applicant restricted = verifiedApplicant("Restricted" + UUID.randomUUID() + "@gmail.com");
        restricted.markRejected("Controlled restriction fixture", clock.instant());
        applicants.saveAndFlush(restricted);
        Applicant exact = verifiedApplicant("ExactCase" + UUID.randomUUID() + "@corp.example.dev");
        int before = delivery.count.get();
        for (String identifier : List.of(unverified.getMobileNumber(), restricted.getEmail(), exact.getEmail().toLowerCase(Locale.ROOT))) {
            var response = tree(call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", identifier))));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, response.path("challengeId").asString()));
        }
        assertEquals(before, delivery.count.get());
    }

    @Test
    void legacyUnknownEmailRequiresExplicitOwnershipRecoveryBeforeNormalLogin() throws Exception {
        String address = email("corp.example.dev");
        Applicant legacy = new Applicant("QA Legacy Applicant", mobile(), address);
        legacy.markEmailVerified(clock.instant(), Applicant.EMAIL_METHOD_LEGACY_UNKNOWN);
        applicants.saveAndFlush(legacy);
        var client = client();
        int before = delivery.count.get();
        var normal = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", address))));
        assertEquals(before, delivery.count.get());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, normal.path("challengeId").asString()));
        clock.advance(61);
        var recovery = tree(call(client, "POST", "/api/v1/auth/recovery/otp/request", json.writeValueAsString(Map.of("identifier", address))));
        String code = delivery.await(address);
        assertEquals(200, call(client, "POST", "/api/v1/auth/recovery/otp/verify", verifyBody(recovery.path("challengeId").asString(), address, code)).statusCode());
        assertTrue(applicants.findById(legacy.getApplicantId()).orElseThrow().isTrustedEmailVerified());
    }

    @Test
    void caseSensitiveContactsHaveIndependentCooldownsAndExactApplicantLookup() throws Exception {
        String suffix = UUID.randomUUID() + "@corp.example.dev";
        Applicant upper = verifiedApplicant("Case" + suffix);
        Applicant lower = verifiedApplicant("case" + suffix);
        for (Applicant applicant : List.of(upper, lower)) {
            var client = client();
            var issued = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", applicant.getEmail()))));
            String code = delivery.await(applicant.getEmail());
            assertEquals(200, call(client, "POST", "/api/v1/auth/login/otp/verify", verifyBody(issued.path("challengeId").asString(), applicant.getEmail(), code)).statusCode());
            assertEquals(applicant.getApplicantId(), jdbc.queryForObject("SELECT applicant_id FROM applicant_auth_challenges WHERE issuance_id=?", Long.class, issued.path("challengeId").asString()));
        }
    }

    @Test
    void legacyAliasesCannotBypassApplicantCooldownOrDiscloseItInPublicResponses() throws Exception {
        Applicant applicant = verifiedApplicant("LegacyCase" + UUID.randomUUID() + "@corp.example.dev");
        org.springframework.test.util.ReflectionTestUtils.setField(applicant, "emailCaseSensitive", false);
        applicants.saveAndFlush(applicant);
        int before = delivery.count.get();
        var first = tree(call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", applicant.getEmail()))));
        delivery.await(applicant.getEmail());
        var alias = call(client(), "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", applicant.getEmail().toUpperCase(Locale.ROOT))));
        assertEquals(200, alias.statusCode());
        assertEquals(first.path("message").asString(), tree(alias).path("message").asString());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_auth_challenges WHERE issuance_id=?", Integer.class, tree(alias).path("challengeId").asString()));
        assertEquals(before + 1, delivery.count.get());
    }

    @Test
    void ipBudgetSurvivesChangesToUnknownIdentifiers() throws Exception {
        var client = client();
        int before = delivery.count.get();
        for (int index = 0; index < 20; index++) {
            assertEquals(200, call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", email("gmail.com")))).statusCode());
        }
        var limited = call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", email("gmail.com"))));
        assertEquals(429, limited.statusCode());
        assertEquals("IP", tree(limited).path("rateLimitScope").asString());
        assertEquals(before, delivery.count.get());
    }

    @Test
    void concurrentOtpVerificationCreatesExactlyOneSuccessfulSessionAndRejectsRecoveryRouteReplay() throws Exception {
        Applicant applicant = verifiedApplicant(email("gmail.com"));
        var first = client(); var second = client();
        var issued = tree(call(first, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", applicant.getEmail()))));
        String code = delivery.await(applicant.getEmail());
        String body = verifyBody(issued.path("challengeId").asString(), applicant.getEmail(), code);
        assertEquals(400, call(second, "POST", "/api/v1/auth/recovery/otp/verify", body).statusCode());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> call(first, "POST", "/api/v1/auth/login/otp/verify", body).statusCode());
            var b = executor.submit(() -> call(second, "POST", "/api/v1/auth/login/otp/verify", body).statusCode());
            var statuses = new ArrayList<>(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
            Collections.sort(statuses);
            assertEquals(List.of(200, 400), statuses);
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_access_sessions WHERE applicant_id=?", Integer.class, applicant.getApplicantId()));
    }

    @Test
    void expiredLoginCodeCreatesNoSession() throws Exception {
        Applicant applicant = verifiedApplicant(email("gmail.com"));
        var client = client();
        var issued = tree(call(client, "POST", "/api/v1/auth/login/otp/request", json.writeValueAsString(Map.of("identifier", applicant.getEmail()))));
        String code = delivery.await(applicant.getEmail());
        clock.advance(301);
        assertEquals(400, call(client, "POST", "/api/v1/auth/login/otp/verify", verifyBody(issued.path("challengeId").asString(), applicant.getEmail(), code)).statusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicant_access_sessions WHERE applicant_id=?", Integer.class, applicant.getApplicantId()));
    }

    @Test
    void linkedGoogleLoginKeepsApplicantPaymentAndMembershipAndBlocksRestrictedIdentity() throws Exception {
        Applicant applicant = paidMember();
        String sub = "qa-sub-" + UUID.randomUUID();
        applicant.linkGoogleIdentity(sub, applicant.getEmail(), null);
        applicants.saveAndFlush(applicant);
        String credential = "fixture-" + UUID.randomUUID();
        googleVerifier.identities.put(credential, new com.zaylope.registration.auth.GoogleTokenVerifierService.GoogleUserPayload(
                sub, applicant.getEmail(), true, applicant.getFullName(), null));
        var client = client();
        assertEquals(200, call(client, "POST", "/api/v1/auth/google", json.writeValueAsString(Map.of("credential", credential, "intent", "LOGIN"))).statusCode());
        assertEquals(applicant.getApplicantId(), applicants.findByGoogleSub(sub).orElseThrow().getApplicantId());
        assertEquals(applicant.getAffiliateId(), tree(call(client, "GET", "/api/v1/auth/session", null)).path("affiliateId").asString());
        assertEquals(1, payments.findByApplicantIdOrderByCreatedAtDesc(applicant.getApplicantId()).size());
        applicant.markRejected("Controlled restriction", clock.instant()); applicants.saveAndFlush(applicant);
        var restricted = client();
        assertEquals(401, call(restricted, "POST", "/api/v1/auth/google", json.writeValueAsString(Map.of("credential", credential, "intent", "LOGIN"))).statusCode());
        assertFalse(tree(call(restricted, "GET", "/api/v1/auth/session", null)).path("authenticated").asBoolean());
    }

    private Applicant verifiedApplicant(String email) {
        Applicant applicant = new Applicant("QA Database Member", mobile(), email);
        applicant.markEmailVerified(clock.instant(), Applicant.EMAIL_METHOD_OTP);
        return applicants.saveAndFlush(applicant);
    }

    private Applicant paidMember() {
        Applicant applicant = verifiedApplicant(email("gmail.com"));
        String reference = UUID.randomUUID().toString().substring(0, 12);
        applicant.recordPrivacyConsent(clock.instant(), "fixture-v1");
        applicant.markRegistrationSubmitted(clock.instant()); applicant.markPaymentPaid();
        applicant.markApproved("ZP-QA-" + reference, clock.instant());
        applicant = applicants.saveAndFlush(applicant);
        var order = new com.zaylope.registration.payment.PaymentOrder(applicant.getApplicantId(), "order_" + reference, 59900L, "INR", "qa_" + reference);
        order.markPaid(); orders.saveAndFlush(order);
        payments.saveAndFlush(new com.zaylope.registration.payment.PaymentTransaction(order.getOrderId(), applicant.getApplicantId(),
                "pay_" + reference, order.getGatewayOrderId(), 59900L, "INR", "upi", "CAPTURED", "fixture-signature", true));
        memberships.saveAndFlush(new com.zaylope.registration.affiliate.AffiliateMembership(applicant.getApplicantId(), applicant.getAffiliateId(), "REF" + reference, null, clock.instant()));
        return applicant;
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "AUTH_BROWSER_TEST", matches = "true")
    void actualBrowserLoginUsesFixtureBackendAndMysqlWithoutApiInterception() throws Exception {
        explicitMode(true);
        Applicant member = paidMember();
        int before = delivery.count.get();
        var builder = new ProcessBuilder("node", "qa/auth-login-live.cjs")
                .directory(java.nio.file.Path.of("../frontend").toFile())
                .redirectError(java.nio.file.Path.of("target/auth-browser-error.log").toFile());
        for (String secret : List.of("DB_PASSWORD", "TEST_DB_PASSWORD", "OTP_TEST_DB_PASSWORD", "OTP_HASH_KEY", "SMTP_PASSWORD", "SUPER_ADMIN_PASSWORD"))
            builder.environment().remove(secret);
        builder.environment().put("AUTH_QA_API_ORIGIN", "http://localhost:" + port);
        Applicant browserDraft = applicants.saveAndFlush(new Applicant("QA Browser Draft", mobile(), email("gmail.com")));
        Applicant browserRestricted = verifiedApplicant(email("gmail.com"));
        browserRestricted.markRejected("Fixture restriction", clock.instant()); applicants.saveAndFlush(browserRestricted);
        builder.environment().put("AUTH_QA_UNVERIFIED", browserDraft.getEmail());
        builder.environment().put("AUTH_QA_RESTRICTED", browserRestricted.getEmail());
        builder.environment().put("AUTH_QA_UNKNOWN_MOBILE", mobile());
        builder.environment().put("AUTH_QA_EMAIL", member.getEmail());
        builder.environment().put("AUTH_QA_AFFILIATE_ID", member.getAffiliateId());
        Process browser = builder.start();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(browser.getInputStream()));
             var writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(browser.getOutputStream()))) {
            var readiness = CompletableFuture.supplyAsync(() -> {
                try { return reader.readLine(); } catch (java.io.IOException failure) { throw new RuntimeException(failure); }
            });
            assertEquals("REQUEST_CODE", readiness.get(40, TimeUnit.SECONDS), "Browser did not reach the private provider handoff");
            // The only code handoff is private process stdin; it is never exposed by an API or log.
            writer.write(delivery.await(member.getEmail())); writer.newLine(); writer.flush(); writer.close();
            assertTrue(browser.waitFor(50, TimeUnit.SECONDS), "Live browser scenario timed out");
            assertEquals(0, browser.exitValue(), "See target/auth-browser-error.log for sanitized browser failure");
            assertEquals(before + 1, delivery.count.get(), "Unknown reported contact must not trigger provider delivery");
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM applicants WHERE email_address='gopi@gm.com'", Integer.class));
            assertEquals(member.getAffiliateId(), applicants.findById(member.getApplicantId()).orElseThrow().getAffiliateId());
            assertEquals(1, payments.findByApplicantIdOrderByCreatedAtDesc(member.getApplicantId()).size());
        } finally {
            // The Node harness closes its own browser/Vite instances; do not enumerate reused parent PIDs on Windows.
            if (browser.isAlive()) browser.destroy();
        }
    }

    private void register(HttpClient client, String email) throws Exception {
        assertEquals(201, call(client, "POST", "/api/v1/applicants/register", registrationBody(email, mobile())).statusCode());
    }
    private String registrationBody(String email, String mobile) throws Exception {
        return json.writeValueAsString(Map.of("fullName", "QA Identity Applicant", "mobileNumber", mobile, "email", email));
    }
    private String verifyBody(String challenge, String email, String code) throws Exception {
        return json.writeValueAsString(Map.of("challengeId", challenge, "identifier", email, "code", code));
    }
    private JsonNode tree(HttpResponse<String> response) { return json.readTree(response.body()); }
    private static HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    private HttpResponse<String> call(HttpClient client, String method, String route, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + route)).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("X-Zaylope-Request", "1")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private long id(String email) { return jdbc.queryForObject("SELECT applicant_id FROM applicants WHERE email_address=?", Long.class, email); }
    private static String email(String domain) { return "qa.identity." + UUID.randomUUID() + "@" + domain; }
    private static String mobile() { return "+919" + String.format(Locale.ROOT, "%09d", SEQUENCE.incrementAndGet()); }

    static class CapturingDelivery implements MobileOtpDelivery, EmailOtpDelivery {
        final ConcurrentHashMap<String, BlockingQueue<String>> codes = new ConcurrentHashMap<>();
        final AtomicInteger count = new AtomicInteger();
        final Set<String> failedDestinations = ConcurrentHashMap.newKeySet();
        @Override public boolean isMock() { return true; }
        @Override public void sendCode(String destination, String code) {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Provider call must be outside a transaction");
            if (failedDestinations.contains(destination)) throw new VerificationDeliveryUnavailableException();
            count.incrementAndGet(); codes.computeIfAbsent(destination, key -> new LinkedBlockingQueue<>()).add(code);
        }
        String await(String destination) throws InterruptedException {
            String code = codes.computeIfAbsent(destination, key -> new LinkedBlockingQueue<>()).poll(5, TimeUnit.SECONDS);
            assertNotNull(code, "No code received by isolated mock provider");
            return code;
        }
    }
    @TestConfiguration
    static class TestBeans {
        @Bean @Primary ControlledGoogleVerifier controlledGoogleVerifier() { return new ControlledGoogleVerifier(); }
        @Bean @Primary FixtureClock fixtureClock() { return new FixtureClock(); }
        @Bean @Primary CapturingDelivery controlledDelivery() { return new CapturingDelivery(); }
        @Bean @Primary EmailDnsResolver controlledDns() {
            return domain -> new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of("10 fixture.invalid."), false);
        }
    }
    static class ControlledGoogleVerifier extends com.zaylope.registration.auth.GoogleTokenVerifierService {
        final Map<String, GoogleUserPayload> identities = new ConcurrentHashMap<>();
        ControlledGoogleVerifier() { super(""); }
        @Override public boolean isConfigured() { return true; }
        @Override public Optional<GoogleUserPayload> verifyToken(String credential) { return Optional.ofNullable(identities.get(credential)); }
    }
    static class FixtureClock extends Clock {
        private final java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(Instant.now());
        void advance(long seconds) { now.updateAndGet(value -> value.plusSeconds(seconds)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }
}

