import assert from 'node:assert/strict';
import test from 'node:test';
import {
    EMAIL_MESSAGE,
    FULL_NAME_MESSAGE,
    LOGIN_IDENTIFIER_MESSAGE,
    MOBILE_MESSAGE,
    extractNationalIndianMobile,
    getEmailDomainSuggestion,
    maskLoginIdentifier,
    normalizeEmail,
    normalizeFullName,
    normalizeIndianMobile,
    normalizeLoginIdentifier,
    normalizePhoneInputValue,
    validateEmail,
    validateFullName,
    validateLoginIdentifier,
    validateMobile,
} from './validation.js';
import { resolvePostAuthRoute } from './authRedirect.js';

test('Full name accepts valid English, initials, punctuation, and Indian Unicode scripts', () => {
    const validCases = [
        ['Sai Sudheer', 'Sai Sudheer'],
        ['  Sai   Sudheer  ', 'Sai Sudheer'],
        ['K. Sai Sudheer', 'K. Sai Sudheer'],
        ['A.P.J. Abdul Kalam', 'A.P.J. Abdul Kalam'],
        ["Mary-Jane O'Connor", "Mary-Jane O'Connor"],
        ['Cher', 'Cher'],
        ['Jose\u0301', 'Jose\u0301'],
        ['సాయి సుధీర్', 'సాయి సుధీర్'],
        ['राहुल शर्मा', 'राहुल शर्मा'],
        ['A'.repeat(150), 'A'.repeat(150)],
    ];

    for (const [input, expected] of validCases) {
        assert.equal(validateFullName(input), '', `Expected valid full name for: ${input}`);
        assert.equal(normalizeFullName(input), expected);
    }
});

test('Full name rejects numbers, emojis, HTML, control characters, punctuation-only, and over-limit strings', () => {
    const invalidCases = [
        '',
        '   ',
        'A',
        'A.',
        'Sai123',
        '123456',
        'Sai@Sudheer',
        '😊 Sai',
        '<script>Sai</script>',
        '...',
        '--',
        "''",
        '-Sai',
        'Sai-',
        "O''Connor",
        'Sai..Kumar',
        'Sai--Kumar',
        'Sai .Kumar',
        'Sai\nSudheer',
        'Sai\u200BSudheer',
        'A'.repeat(151),
    ];

    for (const input of invalidCases) {
        assert.equal(validateFullName(input), FULL_NAME_MESSAGE, `Expected rejection for: ${JSON.stringify(input)}`);
        assert.equal(normalizeFullName(input), null);
    }
});

test('Email accepts standard, plus-tag, subdomain, uppercase domain, and punycode emails', () => {
    const validCases = [
        ['alex@gmail.com', 'alex@gmail.com'],
        ['alex.kumar+work@sub.domain.co.in', 'alex.kumar+work@sub.domain.co.in'],
        ['  Alex.Kumar@GMAIL.com  ', 'Alex.Kumar@gmail.com'],
        ['user@xn--bcher-kva.de', 'user@xn--bcher-kva.de'],
        [`${'a'.repeat(64)}@example.com`, `${'a'.repeat(64)}@example.com`],
    ];

    for (const [input, expected] of validCases) {
        assert.equal(validateEmail(input), '', `Expected valid email for: ${input}`);
        assert.equal(normalizeEmail(input), expected);
    }

    assert.notEqual(normalizeEmail('User@example.com'), normalizeEmail('user@example.com'));
});

test('Email rejects malformed syntax, double dots, invalid labels, numeric/single-char TLDs, and internal spaces', () => {
    const invalidCases = [
        '',
        '   ',
        'alex',
        'alex@',
        '@gmail.com',
        'alex@gmail',
        'alex@gmail..com',
        'alex..kumar@gmail.com',
        '.alex@gmail.com',
        'alex.@gmail.com',
        'alex@-gmail.com',
        'alex@gmail-.com',
        'alex@gmail.c',
        'alex@gmail.con',
        'alex@example.zzzzzz',
        'alex@gmail.123',
        'alex kumar@gmail.com',
        'alex@gm ail.com',
        'alex@@gmail.com',
        'సాయి@gmail.com',
        '<script>@gmail.com',
        'alex\n@gmail.com',
        `${'a'.repeat(65)}@example.com`,
    ];

    for (const input of invalidCases) {
        assert.equal(validateEmail(input), EMAIL_MESSAGE, `Expected rejection for email: ${JSON.stringify(input)}`);
        assert.equal(normalizeEmail(input), null);
    }
});

test('Email typo suggestions are explicit and never count as valid addresses', () => {
    const typoCases = [
        ['alex@gmail.con', 'alex@gmail.com'],
        ['alex@gmail.ocm', 'alex@gmail.com'],
        ['alex@gmial.com', 'alex@gmail.com'],
        ['alex@gmial.ocm', 'alex@gmail.com'],
        ['alex@yahoo.con', 'alex@yahoo.com'],
        ['alex@outlok.com', 'alex@outlook.com'],
    ];

    for (const [input, suggestion] of typoCases) {
        assert.equal(getEmailDomainSuggestion(input), suggestion);
        assert.equal(validateEmail(input), EMAIL_MESSAGE);
        assert.equal(normalizeEmail(input), null);
    }

    assert.equal(getEmailDomainSuggestion('alex@custom-domain.co.in'), null);
    assert.equal(validateEmail('alex@custom-domain.co.in'), '');
});

test('Mobile accepts valid Indian formats and pasted +91 / space / hyphen separations', () => {
    const validCases = [
        ['9876543210', '9876543210', '+919876543210'],
        [' 9876543210 ', '9876543210', '+919876543210'],
        ['+919876543210', '9876543210', '+919876543210'],
        ['+91 9876543210', '9876543210', '+919876543210'],
        ['+91 98765 43210', '9876543210', '+919876543210'],
        ['+91-98765-43210', '9876543210', '+919876543210'],
        ['98765 43210', '9876543210', '+919876543210'],
        ['98765-43210', '9876543210', '+919876543210'],
    ];

    for (const [input, national, e164] of validCases) {
        assert.equal(validateMobile(input), '', `Expected valid mobile for: ${input}`);
        assert.equal(extractNationalIndianMobile(input), national);
        assert.equal(normalizeIndianMobile(input), e164);
    }
});

test('Mobile rejects bad prefixes, wrong digit counts, letters, and malformed separators', () => {
    const invalidCases = [
        '',
        '   ',
        '1234567890',
        '5999999999',
        '987654321',
        '98765432101',
        '919876543210',
        '98765abc10',
        '98765  43210',
        '98765--43210',
        '+1 9876543210',
        '+91  9876543210',
        '+91- 9876543210',
        '9876543210-',
        '-9876543210',
        '(987) 654-3210',
    ];

    for (const input of invalidCases) {
        assert.equal(validateMobile(input), MOBILE_MESSAGE, `Expected rejection for mobile: ${JSON.stringify(input)}`);
        assert.equal(normalizeIndianMobile(input), null);
    }
});

test('Phone field parser accepts national digits and complete Indian autofill/paste, rejects malformed or long input', () => {
    const acceptedCases = [
        ['', ''],
        ['9876', '9876'],
        ['9876543210', '9876543210'],
        ['+91 98765 43210', '9876543210'],
        ['+91-98765-43210', '9876543210'],
        ['98765 43210', '9876543210'],
    ];

    for (const [input, expected] of acceptedCases) {
        assert.equal(normalizePhoneInputValue(input), expected);
    }

    for (const malformed of ['98765abc10', '98765432101', '+1 9876543210', '+91 98765 4321']) {
        assert.equal(normalizePhoneInputValue(malformed), null);
    }
});

test('Login identifier accepts valid Indian mobile or email and rejects malformed values', () => {
    assert.equal(validateLoginIdentifier('9876543210'), '');
    assert.equal(validateLoginIdentifier('+91 98765 43210'), '');
    assert.equal(validateLoginIdentifier('alex.kumar@gmail.com'), '');
    assert.equal(validateLoginIdentifier('user123+zaylo@custom-domain.co.in'), '');
    assert.equal(normalizeLoginIdentifier('Alice.Smith+tag99@GMAIL.com'), 'Alice.Smith+tag99@gmail.com');
    assert.equal(maskLoginIdentifier('9876543210'), '+91 •••••• 3210');
    assert.equal(maskLoginIdentifier('sai18@gmail.com'), 's••••@g••••.com');

    for (const bad of ['', '   ', '1234567890', '5876543210', '98765432101', '98765abc10', 'alex@gmail..com', 'alex@gmail.c', 'alex@gmail.con', 'a@@b.com']) {
        assert.equal(validateLoginIdentifier(bad), LOGIN_IDENTIFIER_MESSAGE);
    }
});

test('Centralized post-auth route resolver maps all 4-step and welcome destinations without review', () => {
    assert.equal(resolvePostAuthRoute('DASHBOARD'), '/welcome');
    assert.equal(resolvePostAuthRoute('WELCOME'), '/welcome');
    assert.equal(resolvePostAuthRoute('ACCOUNT'), '/register?step=1');
    assert.equal(resolvePostAuthRoute('VERIFY'), '/register?step=2');
    assert.equal(resolvePostAuthRoute('PAYMENT'), '/register?step=3');
    assert.equal(resolvePostAuthRoute('PAYMENT_STATUS'), '/register?step=4');
    assert.equal(resolvePostAuthRoute('ACTIVATION_RECOVERY'), '/register?step=4');
    assert.equal(resolvePostAuthRoute('RESTRICTED'), '/register?step=4');
});
