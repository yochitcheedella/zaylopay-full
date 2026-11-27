package com.zaylope.registration.registration;

import com.zaylope.registration.common.exception.InvalidMobileNumberException;

public final class IndianMobileNumber {

	public static final int MAX_INPUT_LENGTH = 24;

	private IndianMobileNumber() {
	}

	public static boolean isValid(String value) {
		return extractNationalTenDigits(value) != null;
	}

	public static String extractNationalTenDigits(String value) {
		if (value == null) {
			return null;
		}
		for (int i = 0; i < value.length(); i++) {
			if (Character.isISOControl(value.charAt(i))) {
				return null;
			}
		}
		String trimmed = value.strip();
		if (trimmed.isEmpty() || trimmed.length() > MAX_INPUT_LENGTH) {
			return null;
		}

		String nationalPart = trimmed;
		if (trimmed.startsWith("+")) {
			if (!trimmed.startsWith("+91")) {
				return null;
			}
			nationalPart = trimmed.substring(3);
			if (!nationalPart.isEmpty() && (nationalPart.charAt(0) == ' ' || nationalPart.charAt(0) == '-')) {
				nationalPart = nationalPart.substring(1);
			}
		}

		if (nationalPart.isEmpty()) {
			return null;
		}

		StringBuilder digits = new StringBuilder(10);
		boolean prevWasSeparator = false;
		for (int i = 0; i < nationalPart.length(); i++) {
			char ch = nationalPart.charAt(i);
			if (ch >= '0' && ch <= '9') {
				if (digits.length() >= 10) {
					return null;
				}
				digits.append(ch);
				prevWasSeparator = false;
			} else if (ch == ' ' || ch == '-') {
				if (digits.isEmpty() || prevWasSeparator) {
					return null;
				}
				prevWasSeparator = true;
			} else {
				return null;
			}
		}

		if (prevWasSeparator || digits.length() != 10) {
			return null;
		}
		char firstDigit = digits.charAt(0);
		if (firstDigit < '6' || firstDigit > '9') {
			return null;
		}
		return digits.toString();
	}

	public static String normalize(String value) {
		String digits = extractNationalTenDigits(value);
		if (digits == null) {
			throw new InvalidMobileNumberException();
		}
		return "+91" + digits;
	}
}
