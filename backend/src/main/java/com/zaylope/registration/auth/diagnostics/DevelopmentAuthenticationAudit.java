package com.zaylope.registration.auth.diagnostics;

import com.zaylope.registration.auth.ApplicantLoginController;
import com.zaylope.registration.config.VerificationProperties;
import java.nio.file.*;
import java.util.LinkedHashMap;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Private, local development diagnostics for the requested schema audit; no public endpoint or identity values. */
@Component
@Profile("dev & !prod & !production & !test")
@ConditionalOnProperty(name = "app.diagnostics.auth-audit-enabled", havingValue = "true", matchIfMissing = true)
public class DevelopmentAuthenticationAudit implements ApplicationRunner {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DevelopmentAuthenticationAudit.class);
    private final DataSource datasource;
    private final ObjectMapper json;
    private final VerificationProperties verification;
    private final boolean mockDelivery;
    private final com.zaylope.registration.auth.ApplicantAuthChallengeService authService;
    private final com.zaylope.registration.applicant.ApplicantRepository applicants;
    private final com.zaylope.registration.verification.EmailOtpDelivery emailDelivery;
    private final com.zaylope.registration.verification.MobileOtpDelivery mobileDelivery;

    public DevelopmentAuthenticationAudit(DataSource datasource, ObjectMapper json, VerificationProperties verification,
            @Value("${app.verification.mock-delivery:false}") boolean mockDelivery,
            com.zaylope.registration.auth.ApplicantAuthChallengeService authService,
            com.zaylope.registration.applicant.ApplicantRepository applicants,
            com.zaylope.registration.verification.EmailOtpDelivery emailDelivery,
            com.zaylope.registration.verification.MobileOtpDelivery mobileDelivery) {
        this.datasource = datasource; this.json = json; this.verification = verification; this.mockDelivery = mockDelivery;
        this.authService = authService; this.applicants = applicants; this.emailDelivery = emailDelivery; this.mobileDelivery = mobileDelivery;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (!isBackend(directory)) directory = directory.resolve("backend");
        if (!isBackend(directory)) {
            LOGGER.warn("Authentication development audit skipped: project output directory not found");
            return;
        }
        try (var connection = datasource.getConnection()) {
            var report = new LinkedHashMap<>(AuthenticationSchemaAudit.inspect(connection, "zaylope"));
            report.put("authContract", ApplicantLoginController.AUTH_CONTRACT);
            report.put("capturedAt", java.time.Instant.now().toString());
            report.put("authServiceImplementation", org.springframework.aop.support.AopUtils.getTargetClass(authService).getName());
            report.put("repositoryImplementation", org.springframework.aop.support.AopUtils.getTargetClass(applicants).getName());
            report.put("emailDeliveryImplementation", org.springframework.aop.support.AopUtils.getTargetClass(emailDelivery).getName());
            report.put("mobileDeliveryImplementation", org.springframework.aop.support.AopUtils.getTargetClass(mobileDelivery).getName());
            report.put("otpDeliveryMode", mockDelivery ? "LOCAL_MOCK" : "PROVIDER_OR_UNCONFIGURED");
            report.put("mobileOtpEnabled", verification.mobileEnabled());
            report.put("emailOtpEnabled", verification.emailEnabled());
            report.put("otpHashConfigured", verification.hasOtpHashKey());
            Path output = directory.resolve("target/auth-runtime-audit.json");
            Files.createDirectories(output.getParent());
            Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
            LOGGER.info("Read-only authentication development audit completed; no identity values or secrets emitted");
        } catch (Exception failure) {
            // Driver exception messages can contain connection information; never log the exception payload.
            LOGGER.warn("Authentication development audit unavailable; inspect configuration with an authorized read-only connection");
        }
    }

    private static boolean isBackend(Path path) {
        return Files.isRegularFile(path.resolve("pom.xml"))
                && Files.isDirectory(path.resolve("src/main/java/com/zaylope/registration"));
    }
}
