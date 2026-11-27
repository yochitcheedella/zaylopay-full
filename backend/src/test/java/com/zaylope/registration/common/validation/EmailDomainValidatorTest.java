package com.zaylope.registration.common.validation;

import com.zaylope.registration.common.exception.InvalidRegistrationInputException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class EmailDomainValidatorTest {
    @Test
    void mxAndSmtpAddressFallbackAcceptDomainsButNotMailboxOwnership() {
        for (var answer : List.of(new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of("10 mx.example.com."), false),
                new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of(), true))) {
            try (var validator = fixture(domain -> answer)) {
                assertDoesNotThrow(() -> validator.validate("nonexistent-mailbox@gmail.com"));
            }
        }
    }
    @Test
    void nonexistentNullMxAndAbsentMailRoutingAreDefinitiveFailures() {
        for (var answer : List.of(new EmailDnsResolver.Answer(EmailDnsResolver.Status.NONEXISTENT, List.of(), false),
                new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of("0 ."), true),
                new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of(), false))) {
            try (var validator = fixture(domain -> answer)) {
                assertThrows(InvalidRegistrationInputException.class, () -> validator.validate("member@gmail.com"));
            }
        }
    }
    @Test
    void temporaryFailureAndTimeoutAreRetryableNeverPermanentInvalidity() {
        try (var validator = fixture(domain -> new EmailDnsResolver.Answer(EmailDnsResolver.Status.TEMPORARY_FAILURE, List.of(), false))) {
            assertThrows(EmailDomainUnavailableException.class, () -> validator.validate("member@gmail.com"));
        }
        try (var validator = fixture(domain -> {
            try { Thread.sleep(300); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of("10 mx.example.com."), false);
        })) {
            long start = System.nanoTime();
            assertThrows(EmailDomainUnavailableException.class, () -> validator.validate("member@gmail.com"));
            assertTrue(System.nanoTime() - start < 250_000_000L);
        }
    }
    @Test
    void cacheIsSharedByDomainAndNeverByMailbox() {
        AtomicInteger lookups = new AtomicInteger();
        try (var validator = fixture(domain -> {
            lookups.incrementAndGet();
            return new EmailDnsResolver.Answer(EmailDnsResolver.Status.ANSWER, List.of("10 mx.example.com."), false);
        })) {
            validator.validate("first@gmail.com"); validator.validate("second@gmail.com");
            assertEquals(1, lookups.get());
        }
    }
    @Test
    void dnsCannotRunWhileDatabaseTransactionIsOpen() {
        try (var validator = fixture(domain -> { fail("DNS must not run"); return null; })) {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try { assertThrows(IllegalStateException.class, () -> validator.validate("member@gmail.com")); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        }
    }
    private static EmailDomainValidator fixture(EmailDnsResolver resolver) {
        return new EmailDomainValidator(EmailContactPolicyTest.properties(true, true), resolver);
    }
}
