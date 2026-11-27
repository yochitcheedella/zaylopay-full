package com.zaylope.registration.session;

public record ApplicantSessionPrincipal(
		long applicantId,
		ApplicantSessionScope sessionScope,
		String sessionToken) {

	public static final String REQUEST_ATTRIBUTE = "applicantSessionPrincipal";

	public ApplicantSessionPrincipal(long applicantId) {
		this(applicantId, ApplicantSessionScope.AUTHENTICATED, null);
	}
}
