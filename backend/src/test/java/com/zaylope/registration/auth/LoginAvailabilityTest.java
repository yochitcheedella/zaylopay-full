package com.zaylope.registration.auth;

import com.zaylope.registration.affiliate.*;
import com.zaylope.registration.applicant.ApplicantRepository;
import com.zaylope.registration.config.VerificationProperties;
import com.zaylope.registration.session.ApplicantSessionService;
import com.zaylope.registration.verification.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginAvailabilityTest {
    @Test void globallyMissingHashKeyNeverPromisesOrPersistsACodeForAnyIdentifier() { assertUnavailable(true, true, ""); }
    @Test void globallyDisabledChannelsFailBeforeAccountLookupForAllIdentifiers() {
        assertUnavailable(false, false, "controlled-test-key-at-least-thirty-two-bytes");
    }
    private void assertUnavailable(boolean mobileEnabled, boolean emailEnabled, String key) {
        var applicants = mock(ApplicantRepository.class);
        var challenges = mock(ApplicantAuthChallengeRepository.class);
        var events = mock(ApplicationEventPublisher.class);
        var mobile = mock(MobileOtpDelivery.class);
        var email = mock(EmailOtpDelivery.class);
        var guard = mock(OtpAbuseGuard.class);
        var sessions = mock(ApplicantSessionService.class);
        var properties = new VerificationProperties(mobileEnabled, emailEnabled, key, Duration.ofMinutes(5), Duration.ofSeconds(60), Duration.ofHours(1), 5, 5);
        var service = new ApplicantAuthChallengeService(applicants, mock(AffiliateMembershipRepository.class),
                new MembershipActivationPolicy(), challenges, events, properties, mobile, email,
                new ApplicantOtpHasher(properties), guard, sessions, Clock.systemUTC());
        for (String identifier : new String[] {"gopi@gm.com", "registered@gmail.com", "+919876543210"}) {
            assertThrows(VerificationDeliveryUnavailableException.class, () -> service.requestLoginOtp(identifier));
        }
        verifyNoInteractions(applicants, challenges, events, mobile, email, guard, sessions);
    }
}
