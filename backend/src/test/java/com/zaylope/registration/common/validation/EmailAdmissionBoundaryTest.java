package com.zaylope.registration.common.validation;

import com.zaylope.registration.common.exception.GlobalExceptionHandler;
import com.zaylope.registration.registration.*;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.applicant.ApplicantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class EmailAdmissionBoundaryTest {
    private final EmailContactPolicy policy = EmailContactPolicyFixtures.policy(true, false);
    @Test
    void directRegistrationApiCannotBypassProviderPolicy() throws Exception {
        var writer = mock(ApplicantRegistrationWriter.class);
        var domains = mock(EmailDomainValidator.class);
        var service = new ApplicantServiceImpl(writer);
        service.configureEmailValidation(policy, domains);
        var mvc = MockMvcBuilders.standaloneSetup(new ApplicantController(service, mock(ApplicantSessionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String domain : new String[] {"kin.com", "gmi.com", "12.12.im", "gmail.com.evil.dev", "mailinator.com"}) {
            mvc.perform(post("/api/v1/applicants/register").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fullName\":\"QA Applicant\",\"mobileNumber\":\"9876543210\",\"email\":\"member@" + domain + "\"}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("email"));
        }
        verifyNoInteractions(writer, domains);
    }
    @Test
    void temporaryDnsFailureReturnsRetryable503BeforePersistence() throws Exception {
        var writer = mock(ApplicantRegistrationWriter.class);
        var domains = mock(EmailDomainValidator.class);
        doThrow(new EmailDomainUnavailableException()).when(domains).validate("member@gmail.com");
        var service = new ApplicantServiceImpl(writer);
        service.configureEmailValidation(policy, domains);
        var mvc = MockMvcBuilders.standaloneSetup(new ApplicantController(service, mock(ApplicantSessionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/v1/applicants/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"QA Applicant\",\"mobileNumber\":\"9876543210\",\"email\":\"member@gmail.com\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_CHECK_UNAVAILABLE"));
        verifyNoInteractions(writer);
    }
    @Test
    void transactionalWriterRepeatsAdmissionChecksForNonHttpCallers() {
        var applicants = mock(ApplicantRepository.class);
        var locks = mock(ApplicantRegistrationLockRepository.class);
        var writer = new ApplicantRegistrationWriter(applicants, locks, mock(ApplicantSessionService.class), policy);
        assertThrows(com.zaylope.registration.common.exception.InvalidRegistrationInputException.class,
                () -> writer.register(new ApplicantRegistrationRequest("QA Applicant", "9876543210", "member@kin.com")));
        verifyNoInteractions(applicants, locks);
    }
    @Test
    void dnsPreflightHappensBeforeWriterAndDoesNotChangeIdentity() {
        var writer = mock(ApplicantRegistrationWriter.class);
        var domains = mock(EmailDomainValidator.class);
        doAnswer(invocation -> { assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return null; })
                .when(domains).validate("Member+tag@gmail.com");
        var service = new ApplicantServiceImpl(writer);
        service.configureEmailValidation(policy, domains);
        var request = new ApplicantRegistrationRequest("QA Applicant", "9876543210", "Member+tag@GMAIL.COM");
        service.register(request);
        var order = inOrder(domains, writer);
        order.verify(domains).validate("Member+tag@gmail.com");
        order.verify(writer).register(request);
    }
}
