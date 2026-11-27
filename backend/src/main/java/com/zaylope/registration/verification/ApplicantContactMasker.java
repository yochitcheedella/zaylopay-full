package com.zaylope.registration.verification;

public final class ApplicantContactMasker {

	private ApplicantContactMasker() {
	}

	public static String maskMobile(String mobileNumber) {
		String digits = mobileNumber.replaceAll("\\D", "");
		return "+91 •••••• " + digits.substring(Math.max(0, digits.length() - 4));
	}

	public static String maskEmail(String emailAddress) {
		int separator = emailAddress.indexOf('@');
		return emailAddress.substring(0, 1) + "••••" + emailAddress.substring(separator);
	}
}
