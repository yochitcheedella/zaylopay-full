package com.zaylope.registration.verification;

import com.zaylope.registration.config.VerificationProperties;
import java.sql.Timestamp;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OtpAbuseGuardTest {
    @Test
    void preLookupLoginUsesDestinationAndIpWithoutSharedApplicantZeroBucket() {
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        var jdbc = mock(JdbcTemplate.class);
        var properties = new VerificationProperties(true, true, "isolated-test-only-key-at-least-32-bytes",
                Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
        when(jdbc.queryForMap(anyString(), any(Object[].class))).thenReturn(Map.of(
                "window_started_at", Timestamp.from(now), "request_count", 0));
        var guard = new OtpAbuseGuard(jdbc, new ApplicantOtpHasher(properties), properties,
                Clock.fixed(now, ZoneOffset.UTC), 15, 20, 100);
        guard.check(0, ApplicantVerificationChannel.EMAIL, "member@gmail.com", "127.0.0.1", true);
        verify(jdbc, times(2)).queryForMap(anyString(), any(Object[].class));
    }
}
