package com.zaylope.registration.session;

import com.zaylope.registration.auth.ApplicantOnboardingSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApplicantSessionCleanup {

	private final ApplicantSessionService applicantSessionService;
	private final ApplicantOnboardingSessionService onboardingSessionService;

	@Autowired
	public ApplicantSessionCleanup(
			ApplicantSessionService applicantSessionService,
			ApplicantOnboardingSessionService onboardingSessionService) {
		this.applicantSessionService = applicantSessionService;
		this.onboardingSessionService = onboardingSessionService;
	}

	public ApplicantSessionCleanup(ApplicantSessionService applicantSessionService) {
		this(applicantSessionService, null);
	}

	@Scheduled(cron = "${applicant.session.cleanup-cron:0 17 2 * * *}")
	@Transactional
	public void deleteExpiredSessions() {
		applicantSessionService.deleteExpiredSessions();
		if (onboardingSessionService != null) {
			onboardingSessionService.deleteExpiredSessions();
		}
	}
}
