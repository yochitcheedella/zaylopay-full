package com.zaylope.registration.common.validation;

import com.zaylope.registration.common.exception.InvalidRegistrationInputException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.junit.jupiter.api.Assertions.*;

class EmailContactPolicyTest {
    static EmailPolicyProperties properties(boolean allowlist, boolean dns) {
        return new EmailPolicyProperties(allowlist, Set.of("gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com",
                "hotmail.com", "live.com", "icloud.com", "proton.me", "protonmail.com", "rediffmail.com"),
                true, "classpath:validation/disposable-email-domains.conf", dns, Duration.ofMillis(50),
                Duration.ofMinutes(5), 8);
    }

    @Test
    void sharedFrontendBackendAdmissionCases() throws Exception {
        var policy = EmailContactPolicyFixtures.policy(true, false);
        try (var reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/contact-validation-cases.txt"), StandardCharsets.UTF_8))) {
            for (String line : reader.lines().filter(s -> !s.startsWith("#") && !s.isBlank()).toList()) {
                String[] row = line.split("\\|");
                assertEquals(Boolean.parseBoolean(row[1]), ApplicantInputValidator.normalizeEmail(row[0]) != null,
                        row[0]);
                if (Boolean.parseBoolean(row[2]))
                    assertNotNull(policy.normalizeNewContact(row[0]), row[0]);
                else
                    assertThrows(InvalidRegistrationInputException.class, () -> policy.normalizeNewContact(row[0]),
                            row[0]);
            }
        }
    }

    @Test
    void configurableCorporateDomainsAndDisposableSuffixBoundaries() {
        var policy = EmailContactPolicyFixtures.policy(false, false);
        assertEquals("Member+Tag@corp.example.dev", policy.normalizeNewContact("Member+Tag@CORP.EXAMPLE.DEV"));
        assertThrows(InvalidRegistrationInputException.class,
                () -> policy.normalizeNewContact("member@sub.mailinator.com"));
        assertFalse(policy.isDisposable("mailinator.com.example.dev"));
    }
}
