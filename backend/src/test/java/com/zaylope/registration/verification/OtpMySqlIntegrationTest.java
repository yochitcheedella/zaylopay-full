package com.zaylope.registration.verification;

import com.zaylope.registration.applicant.*;
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
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.springframework.web.context.request.*;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(OtpMySqlIntegrationTest.TestBeans.class)
@EnabledIfEnvironmentVariable(named="OTP_MYSQL_TEST",matches="true")
class OtpMySqlIntegrationTest {
    static final AtomicInteger sequence=new AtomicInteger((int)(System.currentTimeMillis()%90000000)+1000000);
    static final String URL="jdbc:mysql://127.0.0.1:3308/zaylope_otp_test?allowPublicKeyRetrieval=true&sslMode=DISABLED&connectionTimeZone=UTC";
    @DynamicPropertySource static void configure(DynamicPropertyRegistry registry) throws Exception {
        String password=System.getenv("OTP_TEST_DB_PASSWORD");
        // This test owns only the explicitly named fresh local test schema. Never point it at shared data.
        try(var connection=DriverManager.getConnection(URL,"root",password)) {
            var tables=connection.getMetaData().getTables("zaylope_otp_test",null,"applicants",null);
            String baseline = tables.next() ? "9" : "0";
            org.flywaydb.core.Flyway.configure()
                    .dataSource(URL,"root",password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion(baseline)
                    .load()
                    .migrate();
        }
        registry.add("app.cors.allowed-origin",()->"http://localhost:5180");
        registry.add("spring.datasource.url",()->URL);registry.add("spring.datasource.username",()->"root");registry.add("spring.datasource.password",()->password);
        registry.add("app.verification.mobile-enabled",()->true);registry.add("app.verification.email-enabled",()->true);registry.add("app.verification.mock-delivery",()->true);
        registry.add("app.verification.otp-hash-key",()->"isolated-test-only-key-of-at-least-32-bytes");
        registry.add("spring.jpa.hibernate.ddl-auto",()->"validate"); registry.add("app.documents.enabled",()->false);
    }
    @Autowired ApplicantVerificationService service;
    @Autowired ApplicantRepository applicants;
    @Autowired ApplicantVerificationCodeRepository challenges;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturingDelivery delivery;
    @Autowired MutableClock clock;
    long id;
    Applicant applicant;
    @BeforeEach void applicant() {
        int n=sequence.incrementAndGet();clock.now=Instant.parse("2026-11-20T00:00:00Z");delivery.fail=false;
        applicant=applicants.saveAndFlush(new Applicant("OTP integration sample","+9198"+String.format("%08d",n),"otp-"+UUID.randomUUID()+"@example.test"));id=applicant.getApplicantId();
        var request=new MockHttpServletRequest();request.setRemoteAddr("127."+(n%200)+"."+((n/200)%200)+"."+((n/40000)%200));RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clearRequest(){RequestContextHolder.resetRequestAttributes();}
    @Test void mobileAndEmailAreHashedSingleUseAndPersisted() {
        var dispatch=service.sendMobileCode(id);String mobile=delivery.codes.get(applicant.getMobileNumber());
        assertTrue(mobile.matches("[0-9]{6}"));assertNotEquals(mobile,challenges.findByApplicantApplicantIdAndChannel(id,ApplicantVerificationChannel.MOBILE).orElseThrow().getCodeHash());
        assertFalse(dispatch.toString().contains(mobile));assertEquals(clock.instant().plusSeconds(300),dispatch.expiresAt());
        assertTrue(service.verifyMobileCode(id,mobile).mobileVerified());service.sendEmailCode(id);
        assertTrue(service.verifyEmailCode(id,delivery.codes.get(applicant.getEmail())).emailVerified());
        assertTrue(applicants.findById(id).orElseThrow().isEmailVerified());assertThrows(OtpAlreadyUsedException.class,()->service.verifyMobileCode(id,mobile));
        assertTrue(service.verificationState(id).mobile().verified());
    }
    @Test void wrongAttemptsCommitAndResendsCannotResetCumulativeLimit() {
        for(int challenge=0;challenge<3;challenge++) {
            if(challenge>0)clock.advance(61);service.sendMobileCode(id);String code=delivery.codes.get(applicant.getMobileNumber());String wrong=code.equals("000000")?"000001":"000000";
            for(int attempt=0;attempt<4;attempt++)assertThrows(InvalidOtpException.class,()->service.verifyMobileCode(id,wrong));
            assertThrows(OtpAttemptLimitExceededException.class,()->service.verifyMobileCode(id,wrong));
            assertEquals(5,jdbc.queryForObject("SELECT attempt_count FROM applicant_verification_codes WHERE applicant_id=?",Integer.class,id));
        }
        clock.advance(61);service.sendMobileCode(id);
        assertThrows(OtpRateLimitException.class,()->service.verifyMobileCode(id,delivery.codes.get(applicant.getMobileNumber())));
    }
    @Test void expiryCooldownSendLimitAndReplacementAreDurable() {
        service.sendMobileCode(id);String first=delivery.codes.get(applicant.getMobileNumber());
        assertThrows(OtpRateLimitException.class,()->service.sendMobileCode(id));
        clock.advance(301);assertThrows(OtpExpiredException.class,()->service.verifyMobileCode(id,first));
        service.sendMobileCode(id);var issued=challenges.findByApplicantApplicantIdAndChannel(id,ApplicantVerificationChannel.MOBILE).orElseThrow();
        assertEquals("ACTIVE",issued.getStatus());
        for(int i=0;i<3;i++){clock.advance(61);service.sendMobileCode(id);}
        clock.advance(61);assertThrows(OtpRateLimitException.class,()->service.sendMobileCode(id));
        assertEquals(5,jdbc.queryForObject("SELECT send_count FROM applicant_verification_codes WHERE applicant_id=?",Integer.class,id));
    }
    @Test void providerFailureConsumesBudgetAndCannotVerify() {
        delivery.fail=true;assertThrows(VerificationDeliveryUnavailableException.class,()->service.sendMobileCode(id));
        assertEquals("DELIVERY_FAILED",challenges.findByApplicantApplicantIdAndChannel(id,ApplicantVerificationChannel.MOBILE).orElseThrow().getStatus());
        assertFalse(applicants.findById(id).orElseThrow().isMobileVerified());assertThrows(OtpRateLimitException.class,()->service.sendMobileCode(id));
    }
    @Test void concurrentSendAndVerifyHaveExactlyOneWinner() throws Exception {
        try(var executor=Executors.newFixedThreadPool(2)) {
            var sendCalls=List.<Callable<Boolean>>of(()->attempt(()->service.sendMobileCode(id)),()->attempt(()->service.sendMobileCode(id)));
            assertEquals(1,executor.invokeAll(sendCalls).stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());
            String code=delivery.codes.get(applicant.getMobileNumber());
            var verifyCalls=List.<Callable<Boolean>>of(()->attempt(()->service.verifyMobileCode(id,code)),()->attempt(()->service.verifyMobileCode(id,code)));
            assertEquals(1,executor.invokeAll(verifyCalls).stream().filter(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}).count());
        }
    }
    @Test void changingDestinationInvalidatesVerificationAndOldCode() {
        service.sendMobileCode(id);String old=delivery.codes.get(applicant.getMobileNumber());service.verifyMobileCode(id,old);
        service.changeMobileNumber(id,"+9197"+String.format("%08d",sequence.incrementAndGet()));
        assertFalse(applicants.findById(id).orElseThrow().isMobileVerified());assertThrows(OtpAlreadyUsedException.class,()->service.verifyMobileCode(id,old));
    }

    @Autowired com.zaylope.registration.session.ApplicantSessionService sessions;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired org.springframework.core.env.Environment environment;
    @Test @EnabledIfEnvironmentVariable(named="OTP_BROWSER_TEST",matches="true")
    void browserCompletesVerificationWithCodesCapturedOnlyInMemory() throws Exception {
        String token=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->sessions.createFor(applicant));
        var builder=new ProcessBuilder("node","qa/otp-live-check.cjs").directory(new java.io.File("../frontend"));
        builder.environment().put("OTP_TEST_SESSION",token);
        String browserPhone="+9196"+String.format("%08d",sequence.incrementAndGet());
        builder.environment().put("OTP_TEST_REGISTER_MOBILE",browserPhone);
        builder.environment().put("OTP_TEST_REGISTER_EMAIL","browser-"+UUID.randomUUID()+"@example.test");
        builder.environment().put("OTP_TEST_PORT",environment.getProperty("local.server.port"));
        builder.redirectErrorStream(true).redirectOutput(new java.io.File("target/otp-browser.log"));
        Process child=builder.start();
        delivery.onCode=(destination,code)->{
            synchronized(child){try{child.getOutputStream().write(("{\"channel\":\""+(destination.contains("@")?"email":"mobile")+"\",\"code\":\""+code+"\"}\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));child.getOutputStream().flush();}catch(java.io.IOException failure){throw new IllegalStateException("Browser test channel closed");}}
        };
        try { assertTrue(child.waitFor(150,TimeUnit.SECONDS),"Browser test timed out");assertEquals(0,child.exitValue(),"See target/otp-browser.log"); }
        finally { delivery.onCode=(destination,code)->{};if(child.isAlive())child.destroyForcibly(); }
        assertTrue(applicants.findByMobileNumber(browserPhone).orElseThrow().isEmailVerified());
    }

    @Test void previousCodeIsRejectedAfterReplacementAndEmailChangeClearsStatus() {
        service.sendMobileCode(id);String old=delivery.codes.get(applicant.getMobileNumber());clock.advance(61);service.sendMobileCode(id);
        assertThrows(InvalidOtpException.class,()->service.verifyMobileCode(id,old));
        service.verifyMobileCode(id,delivery.codes.get(applicant.getMobileNumber()));service.sendEmailCode(id);String oldEmail=delivery.codes.get(applicant.getEmail());service.verifyEmailCode(id,oldEmail);
        service.changeEmailAddress(id,"changed-"+UUID.randomUUID()+"@example.test");
        assertFalse(applicants.findById(id).orElseThrow().isEmailVerified());assertThrows(OtpAlreadyUsedException.class,()->service.verifyEmailCode(id,oldEmail));
    }
    @Test void unauthorizedAndExpiredSessionsAreRejectedOverHttp() throws Exception {
        int port=Integer.parseInt(environment.getProperty("local.server.port"));
        var client=java.net.http.HttpClient.newHttpClient();
        var uri=java.net.URI.create("http://localhost:"+port+"/api/v1/applicants/me/verification/status");
        var response=client.send(java.net.http.HttpRequest.newBuilder(uri).header("Origin","http://localhost:5180").GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(401,response.statusCode());assertTrue(response.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
        assertEquals("http://localhost:5180",response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertEquals("true",response.headers().firstValue("Access-Control-Allow-Credentials").orElse(""));
        assertTrue(response.body().contains("\"sessionState\":\"NONE\""));
        String token=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status->sessions.createFor(applicant));
        clock.advance(1801);
        var expired=client.send(java.net.http.HttpRequest.newBuilder(uri).header("Origin","http://localhost:5180").header("Cookie","ZAYLOPE_APPLICANT="+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(401,expired.statusCode());
        assertEquals("http://localhost:5180",expired.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertTrue(expired.body().contains("EXPIRED_OR_INVALID"));
    }
    @Test void durableLimitsProtectDestinationAcrossApplicantsAndIpAcrossDestinations() {
        var guard=new OtpAbuseGuard(jdbc,new ApplicantOtpHasher(new com.zaylope.registration.config.VerificationProperties(true,true,"isolated-test-only-key-of-at-least-32-bytes",Duration.ofMinutes(5),Duration.ofMinutes(1),Duration.ofHours(1),5,5)),new com.zaylope.registration.config.VerificationProperties(true,true,"isolated-test-only-key-of-at-least-32-bytes",Duration.ofMinutes(5),Duration.ofMinutes(1),Duration.ofHours(1),5,5),clock,15,1,100);
        var tx=new org.springframework.transaction.support.TransactionTemplate(transactions);
        String destination="destination-"+UUID.randomUUID();String ip="ip-"+UUID.randomUUID();
        tx.executeWithoutResult(status->guard.check(id,ApplicantVerificationChannel.MOBILE,destination,ip,true));
        assertThrows(OtpRateLimitException.class,()->tx.executeWithoutResult(status->guard.check(id+10000,ApplicantVerificationChannel.MOBILE,destination,ip+"-2",true)));
        assertThrows(OtpRateLimitException.class,()->tx.executeWithoutResult(status->guard.check(id+10001,ApplicantVerificationChannel.MOBILE,destination+"-2",ip,true)));
    }
    @Test void challengeAndCountersSurviveFreshApplicationContext() {
        service.sendMobileCode(id);String code=delivery.codes.get(applicant.getMobileNumber());
        Map<String,Object> settings=new HashMap<>();settings.put("spring.datasource.url",URL);settings.put("spring.datasource.username","root");settings.put("spring.datasource.password",System.getenv("OTP_TEST_DB_PASSWORD"));settings.put("app.verification.mobile-enabled","true");settings.put("app.verification.email-enabled","true");settings.put("app.verification.mock-delivery","true");settings.put("app.verification.otp-hash-key","isolated-test-only-key-of-at-least-32-bytes");settings.put("app.documents.enabled","false");settings.put("spring.jpa.hibernate.ddl-auto","validate");
        try(var restarted=new org.springframework.boot.builder.SpringApplicationBuilder(com.zaylope.registration.ZaylopeRegistrationBackendApplication.class,TestBeans.class)
                .web(org.springframework.boot.WebApplicationType.NONE).profiles("test")
                .initializers(context->context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("isolated-otp-restart",settings))).run()) {
            var fresh=restarted.getBean(ApplicantVerificationService.class);
            assertThrows(OtpRateLimitException.class,()->fresh.sendMobileCode(id));
            assertTrue(fresh.verifyMobileCode(id,code).mobileVerified());
        }
    }

    @Test void generatedLeadingZeroCodeVerifiesAndIsNeverPersistedAsPlaintext() throws Exception {
        Object target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(service);
        var original=org.springframework.test.util.ReflectionTestUtils.getField(target,"secureRandom");
        var random=org.mockito.Mockito.mock(java.security.SecureRandom.class);
        org.mockito.Mockito.when(random.nextInt(1000000)).thenReturn(1234);
        org.springframework.test.util.ReflectionTestUtils.setField(target,"secureRandom",random);
        try {
            service.sendMobileCode(id);String captured=delivery.codes.get(applicant.getMobileNumber());assertEquals("001234",captured);
            assertNotEquals(captured,jdbc.queryForObject("SELECT code_hash FROM applicant_verification_codes WHERE applicant_id=?",String.class,id));
            assertTrue(service.verifyMobileCode(id,captured).mobileVerified());
        } finally { org.springframework.test.util.ReflectionTestUtils.setField(target,"secureRandom",original); }
    }
    private boolean attempt(Runnable task){try{task.run();return true;}catch(OtpRateLimitException|OtpAlreadyUsedException expected){return false;}}
    static class MutableClock extends Clock { volatile Instant now=Instant.parse("2026-11-20T00:00:00Z"); public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}void advance(long seconds){now=now.plusSeconds(seconds);} }
    static class CapturingDelivery implements MobileOtpDelivery,EmailOtpDelivery {final Map<String,String> codes=new ConcurrentHashMap<>();boolean fail; volatile java.util.function.BiConsumer<String,String> onCode=(destination,code)->{}; public boolean isMock(){return true;} public void sendCode(String destination,String code){if(fail)throw new VerificationDeliveryUnavailableException();codes.put(destination,code);onCode.accept(destination,code);} }
    @TestConfiguration static class TestBeans {
        @Bean @Primary MutableClock testClock(){return new MutableClock();}
        @Bean @Primary CapturingDelivery capturedDelivery(){return new CapturingDelivery();}
    }
}

