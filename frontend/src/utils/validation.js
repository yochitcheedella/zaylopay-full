import { messages } from './messages.js';
/**
 * Shared client-side input normalization and validation rules mirroring
 * backend com.zaylope.registration.common.validation.ApplicantInputValidator
 * and IndianMobileNumber.
 *
 * Email policy: validator.js syntax/TLD validation plus explicit ASCII local-part,
 * length, and known-typo rules. Format validation does not prove mailbox ownership.
 */
import isEmail from 'validator/lib/isEmail.js';
import publicTlds from 'tlds/index.json' with { type: 'json' };

export const MAX_FULL_NAME_LENGTH = 150;
export const MIN_FULL_NAME_LENGTH = 2;
export const MAX_RAW_FULL_NAME_LENGTH = 200;
export const MAX_EMAIL_LENGTH = 254;
export const MAX_MOBILE_INPUT_LENGTH = 24;

export const FULL_NAME_MESSAGE = messages.name;
export const EMAIL_MESSAGE = messages.email;
export const MOBILE_MESSAGE = messages.mobile;
export const LOGIN_IDENTIFIER_MESSAGE = messages.identifier;

const UNICODE_LETTER_RE = /^\p{L}$/u;
const UNICODE_MARK_RE = /^\p{M}$/u;
const LOCAL_PART_RE = /^[A-Za-z0-9._%+-]{1,64}$/;
const DOMAIN_LABEL_RE = /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/;
const TLD_RE = /^(?:[a-z]{2,63}|xn--[a-z0-9-]{2,59})$/;
const PUBLIC_TLDS = new Set(publicTlds.map((tld) => tld.toLowerCase()));
const EMAIL_DOMAIN_SUGGESTIONS = Object.freeze({
    'gmail.con': 'gmail.com',
    'gmail.cmo': 'gmail.com',
    'gmail.ocm': 'gmail.com',
    'gmial.com': 'gmail.com',
    'gmial.ocm': 'gmail.com',
    'gmai.com': 'gmail.com',
    'yahoo.con': 'yahoo.com',
    'yaho.com': 'yahoo.com',
    'hotmail.con': 'hotmail.com',
    'outlook.con': 'outlook.com',
    'outlok.com': 'outlook.com',
});
// These are valid domains: offer a correction without blocking custom identities.
const EMAIL_DOMAIN_HINTS = Object.freeze({
    'gmi.com': 'gmail.com',
    'gmal.com': 'gmail.com',
    'yhoo.com': 'yahoo.com',
    'yhaoo.com': 'yahoo.com',
    'outllook.com': 'outlook.com',
    'outook.com': 'outlook.com',
});

function hasDisallowedControlOrInvisible(value) {
    for (const ch of value) {
        const cp = ch.codePointAt(0);
        if (
            cp <= 0x1F
            || (cp >= 0x7F && cp <= 0x9F)
            || ch === '\u200B'
            || ch === '\uFEFF'
            || ch === '<'
            || ch === '>'
        ) {
            return true;
        }
    }
    return false;
}

function validateNameToken(token) {
    const chars = Array.from(token);
    if (chars.length === 0 || !UNICODE_LETTER_RE.test(chars[0])) {
        return -1;
    }

    let letterCount = 1;
    let prevWasLetterOrMark = true;
    let prevWasPunctuation = false;
    let sawTrailingPeriod = false;

    for (let i = 1; i < chars.length; i += 1) {
        const ch = chars[i];
        if (sawTrailingPeriod) {
            // Only another letter (e.g. K.R.) may follow a period within a token.
            if (!UNICODE_LETTER_RE.test(ch)) {
                return -1;
            }
            sawTrailingPeriod = false;
            letterCount += 1;
            prevWasLetterOrMark = true;
            prevWasPunctuation = false;
        } else if (UNICODE_LETTER_RE.test(ch)) {
            letterCount += 1;
            prevWasLetterOrMark = true;
            prevWasPunctuation = false;
        } else if (UNICODE_MARK_RE.test(ch)) {
            if (!prevWasLetterOrMark) {
                return -1;
            }
            prevWasPunctuation = false;
        } else if (ch === '.') {
            if (!prevWasLetterOrMark || prevWasPunctuation) {
                return -1;
            }
            sawTrailingPeriod = true;
            prevWasLetterOrMark = false;
            prevWasPunctuation = true;
        } else if (ch === '-' || ch === "'" || ch === '\u2019') {
            if (!prevWasLetterOrMark || prevWasPunctuation) {
                return -1;
            }
            prevWasLetterOrMark = false;
            prevWasPunctuation = true;
        } else {
            return -1;
        }
    }

    if (prevWasPunctuation && !sawTrailingPeriod) {
        return -1;
    }
    return letterCount;
}

export function normalizeFullName(raw) {
    if (typeof raw !== 'string' || raw.length > MAX_RAW_FULL_NAME_LENGTH) {
        return null;
    }
    if (hasDisallowedControlOrInvisible(raw)) {
        return null;
    }
    const collapsed = raw.trim().replace(/ +/g, ' ');
    if (
        !collapsed
        || collapsed.length < MIN_FULL_NAME_LENGTH
        || collapsed.length > MAX_FULL_NAME_LENGTH
    ) {
        return null;
    }

    let letterCount = 0;
    const tokens = collapsed.split(' ');
    for (const token of tokens) {
        if (!token) {
            return null;
        }
        const tokenLetters = validateNameToken(token);
        if (tokenLetters < 0) {
            return null;
        }
        letterCount += tokenLetters;
    }
    return letterCount >= 2 ? collapsed : null;
}

export function isValidFullName(raw) {
    return normalizeFullName(raw) !== null;
}

export function validateFullName(raw) {
    return isValidFullName(raw) ? '' : FULL_NAME_MESSAGE;
}

export function normalizeEmail(raw) {
    if (typeof raw !== 'string' || hasDisallowedControlOrInvisible(raw)) {
        return null;
    }
    const trimmed = raw.trim();
    if (!trimmed || trimmed.length > MAX_EMAIL_LENGTH || /\s/u.test(trimmed)) {
        return null;
    }
    if (EMAIL_DOMAIN_SUGGESTIONS[trimmed.slice(trimmed.lastIndexOf('@') + 1).toLowerCase()]) {
        return null;
    }
    if (!isEmail(trimmed, {
        allow_display_name: false,
        allow_ip_domain: false,
        allow_utf8_local_part: false,
        require_tld: true,
        ignore_max_length: false,
    })) {
        return null;
    }
    const atIndex = trimmed.indexOf('@');
    if (atIndex <= 0 || atIndex !== trimmed.lastIndexOf('@') || atIndex === trimmed.length - 1) {
        return null;
    }

    const localPart = trimmed.slice(0, atIndex);
    const domainPart = trimmed.slice(atIndex + 1).toLowerCase();
    if (!LOCAL_PART_RE.test(localPart) || localPart.startsWith('.') || localPart.endsWith('.') || localPart.includes('..')) {
        return null;
    }
    if (domainPart.length < 3 || domainPart.length > 253 || domainPart.startsWith('.') || domainPart.endsWith('.') || domainPart.includes('..')) {
        return null;
    }

    const labels = domainPart.split('.');
    if (labels.length < 2) {
        return null;
    }
    for (let i = 0; i < labels.length; i += 1) {
        const label = labels[i];
        if (!DOMAIN_LABEL_RE.test(label)) {
            return null;
        }
        if (i === labels.length - 1 && (!TLD_RE.test(label) || !PUBLIC_TLDS.has(label))) {
            return null;
        }
    }
    return `${localPart}@${domainPart}`;
}

export function isValidEmail(raw) {
    return normalizeEmail(raw) !== null;
}

export function getEmailDomainSuggestion(raw) {
    if (typeof raw !== 'string') {
        return null;
    }

    const trimmed = raw.trim();
    const atIndex = trimmed.lastIndexOf('@');
    if (
        atIndex <= 0
        || atIndex !== trimmed.indexOf('@')
        || trimmed.length > MAX_EMAIL_LENGTH
        || /\s/u.test(trimmed)
        || !LOCAL_PART_RE.test(trimmed.slice(0, atIndex))
    ) {
        return null;
    }

    const domain = trimmed.slice(atIndex + 1).toLowerCase();
    const suggestedDomain = EMAIL_DOMAIN_SUGGESTIONS[domain] || EMAIL_DOMAIN_HINTS[domain];
    return suggestedDomain ? `${trimmed.slice(0, atIndex)}@${suggestedDomain}` : null;
}

export function validateEmail(raw) {
    return isValidEmail(raw) ? '' : EMAIL_MESSAGE;
}

export const EMAIL_PROVIDER_MESSAGE = messages.provider;
export const DISPOSABLE_EMAIL_MESSAGE = messages.disposable;

/** Local feedback from server configuration; the backend repeats all checks on submission. */
export function validateRegistrationEmail(raw, policy) {
    const normalized = normalizeEmail(raw);
    if (!normalized) return EMAIL_MESSAGE;
    if (!policy) return 'Email policy is loading. Please wait or retry.';
    const domain = normalized.slice(normalized.indexOf('@') + 1);
    const blocked = new Set(policy.disposableDomains || []);
    let suffix = domain;
    while (suffix.includes('.')) {
        if (policy.disposableBlockingEnabled && blocked.has(suffix)) return DISPOSABLE_EMAIL_MESSAGE;
        suffix = suffix.slice(suffix.indexOf('.') + 1);
    }
    if (policy.allowlistEnabled && !(policy.allowedDomains || []).includes(domain)) return EMAIL_PROVIDER_MESSAGE;
    return '';
}

export function extractNationalIndianMobile(raw) {
    if (typeof raw !== 'string' || hasDisallowedControlOrInvisible(raw)) {
        return null;
    }
    const trimmed = raw.trim();
    if (!trimmed || trimmed.length > MAX_MOBILE_INPUT_LENGTH) {
        return null;
    }

    let nationalPart = trimmed;
    if (trimmed.startsWith('+')) {
        if (!trimmed.startsWith('+91')) {
            return null;
        }
        nationalPart = trimmed.slice(3);
        if (nationalPart.startsWith(' ') || nationalPart.startsWith('-')) {
            nationalPart = nationalPart.slice(1);
        }
    }
    if (!nationalPart) {
        return null;
    }

    let digits = '';
    let prevWasSeparator = false;
    for (let i = 0; i < nationalPart.length; i += 1) {
        const ch = nationalPart[i];
        if (ch >= '0' && ch <= '9') {
            if (digits.length >= 10) {
                return null;
            }
            digits += ch;
            prevWasSeparator = false;
        } else if (ch === ' ' || ch === '-') {
            if (!digits || prevWasSeparator) {
                return null;
            }
            prevWasSeparator = true;
        } else {
            return null;
        }
    }

    if (prevWasSeparator || digits.length !== 10) {
        return null;
    }
    if (digits[0] < '6' || digits[0] > '9') {
        return null;
    }
    return digits;
}

export function normalizeIndianMobile(raw) {
    const digits = extractNationalIndianMobile(raw);
    return digits ? `+91${digits}` : null;
}

/**
 * Normalizes editable national digits or a complete, well-formed Indian number
 * pasted/autofilled with +91. Returns null instead of truncating malformed input.
 */
export function normalizePhoneInputValue(raw) {
    if (typeof raw !== 'string') {
        return null;
    }
    if (/^[0-9]{0,10}$/.test(raw)) {
        return raw;
    }
    return extractNationalIndianMobile(raw);
}

export function isValidMobile(raw) {
    return extractNationalIndianMobile(raw) !== null;
}

export function validateMobile(raw) {
    return isValidMobile(raw) ? '' : MOBILE_MESSAGE;
}

export function normalizeLoginIdentifier(raw) {
    if (typeof raw !== 'string') {
        return null;
    }
    const trimmed = raw.trim();
    if (!trimmed) {
        return null;
    }
    if (trimmed.includes('@')) {
        return normalizeEmail(trimmed);
    }
    return normalizeIndianMobile(trimmed);
}

export function maskLoginIdentifier(raw) {
    const normalized = normalizeLoginIdentifier(raw);
    if (!normalized) {
        return '';
    }

    if (!normalized.includes('@')) {
        return `+91 •••••• ${normalized.slice(-4)}`;
    }

    const [localPart, domain] = normalized.split('@');
    const labels = domain.split('.');
    const visibleDomain = labels.length > 1
        ? `${labels.slice(0, -1).map((label) => `${label.slice(0, 1)}••••`).join('.')}.${labels.at(-1)}`
        : domain;
    return `${localPart.slice(0, 1)}••••@${visibleDomain}`;
}

export function isValidLoginIdentifier(raw) {
    return normalizeLoginIdentifier(raw) !== null;
}

export function validateLoginIdentifier(raw) {
    return isValidLoginIdentifier(raw) ? '' : LOGIN_IDENTIFIER_MESSAGE;
}

