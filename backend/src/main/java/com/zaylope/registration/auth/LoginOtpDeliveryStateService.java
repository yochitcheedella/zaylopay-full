package com.zaylope.registration.auth;

import com.zaylope.registration.verification.ApplicantVerificationChannel;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginOtpDeliveryStateService {

	private final ApplicantAuthChallengeRepository challengeRepository;
	private final Clock clock;

	public LoginOtpDeliveryStateService(ApplicantAuthChallengeRepository challengeRepository, Clock clock) {
		this.challengeRepository = challengeRepository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Instant> findActiveUnexpiredChallengeExpiration(LoginOtpDispatchEvent event) {
		Instant now = clock.instant();
		return challengeRepository.findByIssuanceIdForUpdate(event.issuanceId())
				.filter(challenge -> isDispatchable(challenge, event) && !challenge.isExpiredAt(now))
				.map(ApplicantAuthChallenge::getExpiresAt);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markDeliveryFailed(String issuanceId) {
		challengeRepository.findByIssuanceIdForUpdate(issuanceId)
				.filter(challenge -> "ACTIVE".equals(challenge.getStatus()) && !challenge.isConsumed())
				.ifPresent(challenge -> {
					challenge.deliveryFailed(clock.instant());
					challengeRepository.saveAndFlush(challenge);
				});
	}

	private boolean isDispatchable(ApplicantAuthChallenge challenge, LoginOtpDispatchEvent event) {
		return (challenge.getPurpose() == ApplicantAuthChallenge.Purpose.LOGIN
						|| challenge.getPurpose() == ApplicantAuthChallenge.Purpose.DRAFT_RECOVERY
						|| challenge.getPurpose() == ApplicantAuthChallenge.Purpose.LEGACY_EMAIL_RECOVERY
						|| challenge.getPurpose() == ApplicantAuthChallenge.Purpose.LINK_GOOGLE)
				&& challenge.getChannel() == event.channel()
				&& "ACTIVE".equals(challenge.getStatus())
				&& !challenge.isConsumed();
	}
}
