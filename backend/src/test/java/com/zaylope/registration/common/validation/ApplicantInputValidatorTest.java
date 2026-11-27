package com.zaylope.registration.common.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicantInputValidatorTest {

    @ParameterizedTest
    @CsvSource({
        "Sai Sudheer, Sai Sudheer",
        "'  Sai   Sudheer  ', Sai Sudheer",
        "K. Sai Sudheer, K. Sai Sudheer",
        "A.P.J. Abdul Kalam, A.P.J. Abdul Kalam",
        "Mary-Jane O'Connor, Mary-Jane O'Connor",
        "Cher, Cher",
        "José, José",
        "సాయి సుధీర్, సాయి సుధీర్",
        "राहुल शर्मा, राहुल शर्मा"
    })
    void acceptsAndNormalizesValidFullNames(String input, String expected) {
        assertTrue(ApplicantInputValidator.isValidFullName(input));
        assertEquals(expected, ApplicantInputValidator.normalizeFullName(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "A",
        "A.",
        "Sai123",
        "123456",
        "Sai@Sudheer",
        "😊 Sai",
        "<script>Sai</script>",
        "...",
        "--",
        "''",
        "-Sai",
        "Sai-",
        "O''Connor",
        "Sai..Kumar",
        "Sai--Kumar",
        "Sai .Kumar",
        "Sai\nSudheer",
        "Sai\tSudheer",
        "Sai\u200BSudheer"
    })
    void rejectsInvalidFullNames(String input) {
        assertFalse(ApplicantInputValidator.isValidFullName(input));
        assertNull(ApplicantInputValidator.normalizeFullName(input));
    }

    @org.junit.jupiter.api.Test
    void enforcesMaxFullNameLength150() {
        String exactly150 = "A".repeat(150);
        String over150 = "A".repeat(151);
        assertTrue(ApplicantInputValidator.isValidFullName(exactly150));
        assertFalse(ApplicantInputValidator.isValidFullName(over150));
    }

    @ParameterizedTest
    @CsvSource({
        "alex@gmail.com, alex@gmail.com",
        "alex.kumar+work@sub.domain.co.in, alex.kumar+work@sub.domain.co.in",
        "'  Alex.Kumar@GMAIL.com  ', Alex.Kumar@gmail.com",
        "user@xn--bcher-kva.de, user@xn--bcher-kva.de",
        "user123+tag@custom-domain.co.in, user123+tag@custom-domain.co.in"
    })
    void acceptsAndNormalizesValidEmails(String input, String expected) {
        assertTrue(ApplicantInputValidator.isValidEmail(input));
        assertEquals(expected, ApplicantInputValidator.normalizeEmail(input));
    }

    @org.junit.jupiter.api.Test
    void preservesEmailLocalPartCaseWhileNormalizingDomain() {
        assertEquals("User@example.com", ApplicantInputValidator.normalizeEmail("User@EXAMPLE.COM"));
        assertEquals("user@example.com", ApplicantInputValidator.normalizeEmail("user@example.com"));
        assertFalse(ApplicantInputValidator.normalizeEmail("User@example.com")
                .equals(ApplicantInputValidator.normalizeEmail("user@example.com")));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "   ",
        "alex",
        "alex@",
        "@gmail.com",
        "alex@gmail",
        "alex@gmail..com",
        "alex..kumar@gmail.com",
        ".alex@gmail.com",
        "alex.@gmail.com",
        "alex@-gmail.com",
        "alex@gmail-.com",
        "alex@gmail.c",
        "alex@gmail.con",
        "alex@gmail.ocm",
        "alex@example.zzzzzz",
        "alex@gmial.com",
        "alex@gmial.ocm",
        "alex@outlok.com",
        "alex@gmail.123",
        "alex kumar@gmail.com",
        "alex@gm ail.com",
        "alex@@gmail.com",
        "సాయి@gmail.com",
        "<script>@gmail.com",
        "alex\n@gmail.com"
    })
    void rejectsInvalidEmails(String input) {
        assertFalse(ApplicantInputValidator.isValidEmail(input));
        assertNull(ApplicantInputValidator.normalizeEmail(input));
    }

    @org.junit.jupiter.api.Test
    void acceptsCustomDomainsAndRejectsKnownProviderDomainTypos() {
        assertTrue(ApplicantInputValidator.isValidEmail("person@business-domain.dev"));
        assertFalse(ApplicantInputValidator.isValidEmail("person@gmail.con"));
        assertFalse(ApplicantInputValidator.isValidEmail("person@gmial.com"));
    }

    @org.junit.jupiter.api.Test
    void enforcesMaxEmailLength254AndLocalPart64() {
        String validLongLocal = "a".repeat(64) + "@example.com";
        String overLocal = "a".repeat(65) + "@example.com";
        assertTrue(ApplicantInputValidator.isValidEmail(validLongLocal));
        assertFalse(ApplicantInputValidator.isValidEmail(overLocal));

        String label63 = "a".repeat(63);
        String domain240 = label63 + "." + label63 + "." + label63 + "." + "c".repeat(44) + ".com";
        assertEquals(254, ("user123456789@" + domain240).length());
        assertTrue(ApplicantInputValidator.isValidEmail("user123456789@" + domain240));
        assertFalse(ApplicantInputValidator.isValidEmail("user1234567890@" + domain240));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "9876543210",
        "+91 9876543210",
        "alex.kumar@gmail.com"
    })
    void acceptsValidLoginIdentifiers(String input) {
        assertTrue(ApplicantInputValidator.isValidLoginIdentifier(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "1234567890",
        "98765432101",
        "98765abc10",
        "alex@gmail..com",
        "alex@gmail.c",
        "alex@gmail"
    })
    void rejectsInvalidLoginIdentifiers(String input) {
        assertFalse(ApplicantInputValidator.isValidLoginIdentifier(input));
        assertNull(ApplicantInputValidator.normalizeLoginIdentifier(input));
    }
}
