package com.zaylope.registration.common.validation;

import com.zaylope.registration.affiliate.AffiliateMembershipRepository;
import com.zaylope.registration.applicant.Applicant;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.auth.ApplicantAuthChallengeService;
import com.zaylope.registration.auth.ApplicantLoginController;
import com.zaylope.registration.auth.ApplicantOnboardingSessionService;
import com.zaylope.registration.auth.GoogleAuthController;
import com.zaylope.registration.auth.GoogleAuthService;
import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.common.exception.InvalidRegistrationInputException;
import com.zaylope.registration.config.ApplicantSessionProperties;
import com.zaylope.registration.config.RegistrationProperties;
import com.zaylope.registration.registration.ApplicantController;
import com.zaylope.registration.registration.ApplicantRegistrationLockRepository;
import com.zaylope.registration.registration.ApplicantRegistrationRequest;
import com.zaylope.registration.registration.ApplicantRegistrationResponse;
import com.zaylope.registration.registration.ApplicantRegistrationResult;
import com.zaylope.registration.registration.ApplicantRegistrationWriter;
import com.zaylope.registration.registration.ApplicantService;
import com.zaylope.registration.session.ApplicantAccessSessionRepository;
import com.zaylope.registration.session.ApplicantSessionPrincipal;
import com.zaylope.registration.session.ApplicantSessionScope;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.ApplicantVerificationController;
import com.zaylope.registration.verification.ApplicantVerificationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ValidationSecurityApiTest {

    private ApplicantService applicantService;
    private GoogleAuthService googleAuthService;
    private ApplicantVerificationService verificationService;
    private ApplicantAuthChallengeService authChallengeService;
    private LocalValidatorFactoryBean validator;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        applicantService = mock(ApplicantService.class);
        googleAuthService = mock(GoogleAuthService.class);
        verificationService = mock(ApplicantVerificationService.class);
        authChallengeService = mock(ApplicantAuthChallengeService.class);
        ApplicantSessionService sessionService = new ApplicantSessionService(
                mock(ApplicantAccessSessionRepository.class),
                new ApplicantSessionProperties("ZAYLOPE_APPLICANT", Duration.ofMinutes(30), false),
                Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC));

        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ApplicantController(applicantService, sessionService),
                        new GoogleAuthController(googleAuthService, sessionService),
                        new ApplicantVerificationController(verificationService),
                        new ApplicantLoginController(
                                mock(ApplicantRepository.class),
                                sessionService,
                                mock(ApplicantOnboardingSessionService.class),
                                authChallengeService,
                                mock(AffiliateMembershipRepository.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        validator.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Sai123",
        "123456",
        "Sai@Sudheer",
        "😊 Sai",
        "<script>Sai</script>",
        "...",
        "--",
        "-Sai",
        "A"
    })
    void registerRejectsInvalidFullNamesBypassingFrontend(String badName) throws Exception {
        String body = "{\"fullName\":" + toJsonString(badName)
                + ",\"mobileNumber\":\"9876543210\",\"email\":\"alex@gmail.com\"}";

        mockMvc.perform(post("/api/v1/applicants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.errors[*].message", hasItem(ApplicantInputValidator.FULL_NAME_MESSAGE)));

        verify(applicantService, never()).register(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "alex@gmail..com",
        "alex..kumar@gmail.com",
        ".alex@gmail.com",
        "alex@gmail.c",
        "alex@gmail.con",
        "alex@example.zzzzzz",
        "alex@gmail.123",
        "alex kumar@gmail.com",
        "alex@gmail"
    })
    void registerRejectsInvalidEmailsBypassingFrontend(String badEmail) throws Exception {
        String body = "{\"fullName\":\"Sai Sudheer\",\"mobileNumber\":\"9876543210\",\"email\":"
                + toJsonString(badEmail) + "}";

        mockMvc.perform(post("/api/v1/applicants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("email")))
                .andExpect(jsonPath("$.errors[*].message", hasItem(ApplicantInputValidator.EMAIL_MESSAGE)));

        verify(applicantService, never()).register(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1234567890",
        "987654321",
        "98765432101",
        "98765abc10",
        "+1 9876543210"
    })
    void registerRejectsInvalidMobilesBypassingFrontend(String badMobile) throws Exception {
        String body = "{\"fullName\":\"Sai Sudheer\",\"mobileNumber\":"
                + toJsonString(badMobile) + ",\"email\":\"alex@gmail.com\"}";

        mockMvc.perform(post("/api/v1/applicants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("mobileNumber")))
                .andExpect(jsonPath("$.errors[*].message", hasItem(ApplicantInputValidator.MOBILE_MESSAGE)));

        verify(applicantService, never()).register(any());
    }

    @Test
    void registerAcceptsUnicodeNamePlusEmailAndSeparatedIndianMobile() throws Exception {
        when(applicantService.register(any(ApplicantRegistrationRequest.class)))
                .thenReturn(new ApplicantRegistrationResult(
                        new ApplicantRegistrationResponse("Registration draft created successfully", "DRAFT"),
                        "token-123"));

        mockMvc.perform(post("/api/v1/applicants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "K. Sai Sudheer",
                                  "mobileNumber": "+91 98765 43210",
                                  "email": "alex.kumar+work@sub.domain.co.in"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void registerRejectsMalformedJsonAndWrongType() throws Exception {
        mockMvc.perform(post("/api/v1/applicants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":[\"bad\"],\"mobileNumber\":9876543210,\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void googleCompleteProfileRejectsInvalidFullNameAndMobile() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google/complete-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Sai123\",\"mobileNumber\":\"98765432101\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("fullName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("mobileNumber")));

        verify(googleAuthService, never()).completeProfile(any(), any(), any(), any());
    }

    @Test
    void contactChangeEndpointsRejectInvalidMobileAndEmail() throws Exception {
        mockMvc.perform(put("/api/v1/applicants/me/verification/mobile")
                        .requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(11L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\":\"98765abc10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(put("/api/v1/applicants/me/verification/email")
                        .requestAttr(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE, new ApplicantSessionPrincipal(11L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alex@gmail..com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verify(verificationService, never()).changeMobileNumber(anyLong(), any());
        verify(verificationService, never()).changeEmailAddress(anyLong(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "98765abc10", "alex@gmail..com", "alex@gmail.c"})
    void loginOtpRequestRejectsInvalidIdentifier(String badIdentifier) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":" + toJsonString(badIdentifier) + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("identifier")));

        verify(authChallengeService, never()).requestLoginOtp(any());
    }

    @Test
    void writerServiceRejectsInvalidInputsAndNormalizesValidPersistence() {
        ApplicantRepository applicantRepo = mock(ApplicantRepository.class);
        ApplicantRegistrationLockRepository lockRepo = mock(ApplicantRegistrationLockRepository.class);
        ApplicantSessionService sessionSvc = mock(ApplicantSessionService.class);
        when(lockRepo.findByMobileNumberForUpdate(any())).thenReturn(Optional.empty());
        when(lockRepo.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(applicantRepo.saveAndFlush(any())).thenAnswer(inv -> {
            Applicant saved = inv.getArgument(0);
            try {
                var f = Applicant.class.getDeclaredField("applicantId");
                f.setAccessible(true);
                f.set(saved, 99L);
            } catch (ReflectiveOperationException ignored) {
            }
            return saved;
        });
        when(sessionSvc.createFor(any(Applicant.class), any(ApplicantSessionScope.class))).thenReturn("sess-99");

        ApplicantRegistrationWriter writer = new ApplicantRegistrationWriter(
                applicantRepo, lockRepo, sessionSvc,
                EmailContactPolicyFixtures.policy(true, false),
                new RegistrationProperties(Duration.ofMinutes(30)),
                Clock.fixed(Instant.parse("2026-11-26T10:00:00Z"), ZoneOffset.UTC));

        assertThrows(InvalidRegistrationInputException.class,
                () -> writer.register(new ApplicantRegistrationRequest("Sai123", "9876543210", "alex@gmail.com")));
        assertThrows(InvalidRegistrationInputException.class,
                () -> writer.register(new ApplicantRegistrationRequest("Sai Sudheer", "9876543210", "alex@gmail..com")));

        writer.register(new ApplicantRegistrationRequest(
                "  Sai   Sudheer  ",
                "+91 98765 43210",
                "  Alex.Kumar+work@GMAIL.com  "));

        ArgumentCaptor<Applicant> savedCaptor = ArgumentCaptor.forClass(Applicant.class);
        verify(applicantRepo, Mockito.atLeastOnce()).saveAndFlush(savedCaptor.capture());
        Applicant persisted = savedCaptor.getAllValues().get(0);
        assertEquals("Sai Sudheer", persisted.getFullName());
        assertEquals("+919876543210", persisted.getMobileNumber());
        assertEquals("Alex.Kumar+work@gmail.com", persisted.getEmail());
    }

    private static String toJsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\n");
            } else if (c == '\r') {
                sb.append("\\r");
            } else if (c == '\t') {
                sb.append("\\t");
            } else {
                sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
