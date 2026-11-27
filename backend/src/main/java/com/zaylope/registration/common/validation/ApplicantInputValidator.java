package com.zaylope.registration.common.validation;

import com.zaylope.registration.registration.IndianMobileNumber;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.validator.routines.EmailValidator;

/**
 * Shared server-authoritative input normalization and validation rules for ZayloPe onboarding and login.
 *
 * <p>Email policy: ASCII local part + LDH/Punycode ({@code xn--...}) domain policy. Raw non-ASCII
 * Unicode local parts or domain labels are rejected to prevent homoglyph ambiguity unless domains are
 * punycode-encoded.
 */
public final class ApplicantInputValidator {

    public static final int MIN_FULL_NAME_LENGTH = 2;
    public static final int MAX_FULL_NAME_LENGTH = 150;
    public static final int MAX_RAW_FULL_NAME_LENGTH = 200;
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_LOCAL_PART_LENGTH = 64;

    public static final String FULL_NAME_MESSAGE =
            "Enter a valid name without numbers or unrelated symbols.";
    public static final String EMAIL_MESSAGE = "Enter a valid email address.";
    public static final String MOBILE_MESSAGE = "Enter a valid 10-digit mobile number.";
    public static final String LOGIN_IDENTIFIER_MESSAGE =
            "Enter a valid 10-digit mobile number or email address.";

    private static final Pattern MULTI_SPACE = Pattern.compile(" +");
    private static final Pattern LOCAL_PART_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]{1,64}$");
    private static final Pattern DOMAIN_LABEL_PATTERN =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern TLD_PATTERN =
            Pattern.compile("^(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})$");
    private static final Map<String, String> EMAIL_DOMAIN_SUGGESTIONS = Map.ofEntries(
            Map.entry("gmail.con", "gmail.com"),
            Map.entry("gmail.cmo", "gmail.com"),
            Map.entry("gmail.ocm", "gmail.com"),
            Map.entry("gmial.com", "gmail.com"),
            Map.entry("gmial.ocm", "gmail.com"),
            Map.entry("gmai.com", "gmail.com"),
            Map.entry("yahoo.con", "yahoo.com"),
            Map.entry("yaho.com", "yahoo.com"),
            Map.entry("hotmail.con", "hotmail.com"),
            Map.entry("outlook.con", "outlook.com"),
            Map.entry("outlok.com", "outlook.com"));
    private static final EmailValidator EMAIL_VALIDATOR = EmailValidator.getInstance(false, false);

    private ApplicantInputValidator() {
    }

    public static String normalizeFullName(String raw) {
        if (raw == null || raw.length() > MAX_RAW_FULL_NAME_LENGTH) {
            return null;
        }
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            if (isDisallowedControlOrInvisible(cp)) {
                return null;
            }
            i += Character.charCount(cp);
        }
        String collapsed = MULTI_SPACE.matcher(raw.strip()).replaceAll(" ");
        if (collapsed.isEmpty() || collapsed.length() < MIN_FULL_NAME_LENGTH
                || collapsed.length() > MAX_FULL_NAME_LENGTH) {
            return null;
        }

        int letterCount = 0;
        String[] tokens = collapsed.split(" ", -1);
        for (String token : tokens) {
            if (token.isEmpty()) {
                return null;
            }
            int tokenLetters = validateNameToken(token);
            if (tokenLetters < 0) {
                return null;
            }
            letterCount += tokenLetters;
        }
        return letterCount >= 2 ? collapsed : null;
    }

    public static boolean isValidFullName(String raw) {
        return normalizeFullName(raw) != null;
    }

    private static int validateNameToken(String token) {
        int index = 0;
        int len = token.length();
        int firstCp = token.codePointAt(0);
        if (!Character.isLetter(firstCp)) {
            return -1;
        }
        int letterCount = 1;
        boolean prevWasLetterOrMark = true;
        boolean prevWasPunctuation = false;
        boolean sawTrailingPeriod = false;
        index += Character.charCount(firstCp);

        while (index < len) {
            int cp = token.codePointAt(index);
            if (sawTrailingPeriod) {
                // Only another letter (e.g. K.R.) may follow a period within the same token.
                if (!Character.isLetter(cp)) {
                    return -1;
                }
                sawTrailingPeriod = false;
                letterCount++;
                prevWasLetterOrMark = true;
                prevWasPunctuation = false;
            } else if (Character.isLetter(cp)) {
                letterCount++;
                prevWasLetterOrMark = true;
                prevWasPunctuation = false;
            } else if (isCombiningMark(cp)) {
                if (!prevWasLetterOrMark) {
                    return -1;
                }
                prevWasPunctuation = false;
            } else if (cp == '.') {
                if (!prevWasLetterOrMark || prevWasPunctuation) {
                    return -1;
                }
                sawTrailingPeriod = true;
                prevWasLetterOrMark = false;
                prevWasPunctuation = true;
            } else if (cp == '-' || cp == '\'' || cp == '\u2019') {
                if (!prevWasLetterOrMark || prevWasPunctuation) {
                    return -1;
                }
                prevWasLetterOrMark = false;
                prevWasPunctuation = true;
            } else {
                return -1;
            }
            index += Character.charCount(cp);
        }

        if (prevWasPunctuation && !sawTrailingPeriod) {
            return -1;
        }
        return letterCount;
    }

    private static boolean isCombiningMark(int cp) {
        int type = Character.getType(cp);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isDisallowedControlOrInvisible(int cp) {
        return Character.isISOControl(cp)
                || cp == '\u200B'
                || cp == '\uFEFF'
                || cp == '<'
                || cp == '>';
    }

    public static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            if (isDisallowedControlOrInvisible(cp)) {
                return null;
            }
            i += Character.charCount(cp);
        }
        String trimmed = raw.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_EMAIL_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                return null;
            }
        }
        int atIndex = trimmed.indexOf('@');
        if (atIndex <= 0 || atIndex != trimmed.lastIndexOf('@') || atIndex == trimmed.length() - 1) {
            return null;
        }
        String localPart = trimmed.substring(0, atIndex);
        String domainPart = trimmed.substring(atIndex + 1).toLowerCase(Locale.ROOT);
        if (EMAIL_DOMAIN_SUGGESTIONS.containsKey(domainPart)
                || !EMAIL_VALIDATOR.isValid(trimmed)
                || !isValidLocalPart(localPart)
                || !isValidDomainPart(domainPart)) {
            return null;
        }
        return localPart + "@" + domainPart;
    }

    public static boolean isValidEmail(String raw) {
        return normalizeEmail(raw) != null;
    }

    private static boolean isValidLocalPart(String localPart) {
        if (localPart.length() > MAX_LOCAL_PART_LENGTH || !LOCAL_PART_PATTERN.matcher(localPart).matches()) {
            return false;
        }
        if (localPart.startsWith(".") || localPart.endsWith(".") || localPart.contains("..")) {
            return false;
        }
        return true;
    }

    private static boolean isValidDomainPart(String domainPart) {
        if (domainPart.length() < 3 || domainPart.length() > 253) {
            return false;
        }
        if (domainPart.startsWith(".") || domainPart.endsWith(".") || domainPart.contains("..")) {
            return false;
        }
        String[] labels = domainPart.split("\\.", -1);
        if (labels.length < 2) {
            return false;
        }
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            if (!DOMAIN_LABEL_PATTERN.matcher(label).matches()) {
                return false;
            }
            if (i == labels.length - 1 && !TLD_PATTERN.matcher(label).matches()) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidLoginIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String trimmed = raw.strip();
        if (trimmed.contains("@")) {
            return isValidEmail(trimmed);
        }
        return IndianMobileNumber.isValid(trimmed);
    }

    public static String normalizeLoginIdentifier(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.strip();
        if (trimmed.contains("@")) {
            return normalizeEmail(trimmed);
        }
        return IndianMobileNumber.isValid(trimmed) ? IndianMobileNumber.normalize(trimmed) : null;
    }
}
