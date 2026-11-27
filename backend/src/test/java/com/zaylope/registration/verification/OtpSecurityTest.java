package com.zaylope.registration.verification;

import com.zaylope.registration.config.VerificationProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OtpSecurityTest {
    private final ApplicantOtpHasher hasher = new ApplicantOtpHasher(new VerificationProperties(true,true,
        "test-only-key-with-at-least-32-bytes", Duration.ofMinutes(5),Duration.ofMinutes(1),Duration.ofHours(1),5,5));

    @Test void bindsLeadingZeroCodeToIssuanceApplicantDestinationAndChannel() {
        String hash = hasher.hash("issuance-1", 1, ApplicantVerificationChannel.MOBILE, "+919876543210", "001234");
        assertTrue(hasher.matches(hash,"issuance-1",1,ApplicantVerificationChannel.MOBILE,"+919876543210","001234"));
        assertFalse(hasher.matches(hash,"issuance-2",1,ApplicantVerificationChannel.MOBILE,"+919876543210","001234"));
        assertFalse(hasher.matches(hash,"issuance-1",2,ApplicantVerificationChannel.MOBILE,"+919876543210","001234"));
        assertFalse(hasher.matches(hash,"issuance-1",1,ApplicantVerificationChannel.EMAIL,"+919876543210","001234"));
        assertFalse(hasher.matches(hash,"issuance-1",1,ApplicantVerificationChannel.MOBILE,"+919876543211","001234"));
        assertFalse(hasher.matches(hash,"issuance-1",1,ApplicantVerificationChannel.MOBILE,"+919876543210","1234"));
        assertEquals(64,hash.length());
    }

    @Test void resendCreatesFreshIssuanceAndPreservesCreationTime() {
        Instant now=Instant.parse("2026-11-20T00:00:00Z");
        var applicant=new com.zaylope.registration.applicant.Applicant("Sample","+919876543210","sample@example.test");
        var challenge=new ApplicantVerificationCode(applicant,ApplicantVerificationChannel.MOBILE,"a".repeat(64),now,now.plusSeconds(300),now.plusSeconds(60),1);
        String issuance=challenge.getIssuanceId();
        challenge.resend("b".repeat(64),now.plusSeconds(61),now.plusSeconds(361),now.plusSeconds(121),Duration.ofHours(1),5);
        assertNotEquals(issuance,challenge.getIssuanceId());
        assertEquals(now,challenge.getCreatedAt());
    }
}
