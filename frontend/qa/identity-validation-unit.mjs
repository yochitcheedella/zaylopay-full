import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { normalizeEmail, validateRegistrationEmail, validateLoginIdentifier } from '../src/utils/validation.js';

export const policy = {
  allowlistEnabled: true,
  allowedDomains: ['gmail.com', 'yahoo.com', 'yahoo.co.in', 'outlook.com', 'hotmail.com', 'live.com',
    'icloud.com', 'proton.me', 'protonmail.com', 'rediffmail.com'],
  disposableBlockingEnabled: true,
  disposableDomains: fs.readFileSync(new URL('../../backend/src/main/resources/validation/disposable-email-domains.conf', import.meta.url), 'utf8')
    .split(/\r?\n/).map(line => line.trim()).filter(line => line && !line.startsWith('#')),
};

const cases = fs.readFileSync(new URL('../../backend/src/test/resources/contact-validation-cases.txt', import.meta.url), 'utf8')
  .split(/\r?\n/).filter(line => line && !line.startsWith('#'));
for (const row of cases) {
  const [email, syntaxValid, admitted] = row.split('|');
  test(`shared frontend/backend case: ${email}`, () => {
    assert.equal(normalizeEmail(email) !== null, syntaxValid === 'true');
    assert.equal(validateRegistrationEmail(email, policy) === '', admitted === 'true');
  });
}

test('new admission policy never locks authorized custom-domain identities out of login', () => {
  assert.notEqual(validateRegistrationEmail('Member@corp.example.dev', policy), '');
  assert.equal(validateLoginIdentifier('Member@corp.example.dev'), '');
});
test('corporate policy extension and disposable exact-label boundary', () => {
  assert.equal(validateRegistrationEmail('Member@corp.example.dev', { ...policy, allowedDomains: [...policy.allowedDomains, 'corp.example.dev'] }), '');
  assert.equal(validateRegistrationEmail('Member@mailinator.com.example.dev', { ...policy, allowlistEnabled: false }), '');
  assert.match(validateRegistrationEmail('Member@sub.mailinator.com', { ...policy, allowlistEnabled: false }), /permanent email address/);
});
test('unavailable policy is a retryable UI state, never green validation', () => {
  assert.match(validateRegistrationEmail('member@gmail.com', null), /loading/);
});

