package com.zaylope.registration.verification;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactOtpDeliveryStateService {
    private final ApplicantVerificationCodeRepository codes;
    private final ApplicantOtpHasher hasher;
    private final Clock clock;
    public ContactOtpDeliveryStateService(ApplicantVerificationCodeRepository codes, ApplicantOtpHasher hasher, Clock clock) {
        this.codes = codes; this.hasher = hasher; this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Instant> expiration(ContactOtpDispatchEvent event) {
        String fingerprint = hasher.fingerprint("DESTINATION:" + event.channel() + ":" + event.destination());
        return codes.findByApplicantAndChannelForUpdate(event.applicantId(), event.channel())
                .filter(code -> event.issuanceId().equals(code.getIssuanceId()) && code.isActiveFor(fingerprint)
                        && !code.isConsumed() && !code.isExpiredAt(clock.instant()))
                .map(ApplicantVerificationCode::getExpiresAt);
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(ContactOtpDispatchEvent event) {
        codes.findByApplicantAndChannelForUpdate(event.applicantId(), event.channel())
                .filter(code -> event.issuanceId().equals(code.getIssuanceId()) && "ACTIVE".equals(code.getStatus()))
                .ifPresent(code -> { code.deliveryFailed(clock.instant()); codes.saveAndFlush(code); });
    }
}
