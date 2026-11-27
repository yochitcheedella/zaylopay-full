import test from 'node:test';
import assert from 'node:assert/strict';
import { getEmailDomainSuggestion, normalizeLoginIdentifier, validateLoginIdentifier } from '../src/utils/validation.js';
import { resolvePostAuthRoute } from '../src/utils/authRedirect.js';

test('valid custom domains preserve local-part case and normalize only domain', () => {
  assert.equal(normalizeLoginIdentifier(' Member+tag@CUSTOM-DOMAIN.DEV '), 'Member+tag@custom-domain.dev');
  assert.equal(validateLoginIdentifier('person@independent-business.in'), '');
});

test('advisory Gmail, Yahoo and Outlook suggestions do not invalidate real domains', () => {
  for (const [domain, suggestion] of [['gmi.com', 'gmail.com'], ['yhoo.com', 'yahoo.com'], ['outllook.com', 'outlook.com']]) {
    assert.equal(getEmailDomainSuggestion(`Member@${domain}`), `Member@${suggestion}`);
    assert.equal(normalizeLoginIdentifier(`Member@${domain}`), `Member@${domain}`);
  }
});

test('malformed addresses and existing strict typo policy are rejected', () => {
  for (const value of ['a@@gmail.com', 'a..b@gmail.com', 'a@gmail..com', 'a@gmail.con', 'a b@example.com']) {
    assert.equal(normalizeLoginIdentifier(value), null);
  }
});

test('Indian mobile normalization is exact and never truncates invalid numbers', () => {
  for (const value of ['9876543210', '+91 98765 43210', '+91-98765-43210']) {
    assert.equal(normalizeLoginIdentifier(value), '+919876543210');
  }
  for (const value of ['98765432101', '+1 9876543210', '5123456789', '12345']) {
    assert.equal(normalizeLoginIdentifier(value), null);
  }
});

test('incomplete applicants resume the four-step registration journey', () => {
  assert.equal(resolvePostAuthRoute('ACCOUNT'), '/register?step=1');
  assert.equal(resolvePostAuthRoute('VERIFY'), '/register?step=2');
  assert.equal(resolvePostAuthRoute('PAYMENT'), '/register?step=3');
  assert.equal(resolvePostAuthRoute('ACTIVATION_RECOVERY'), '/register?step=4');
});

test('active members reach welcome and restricted applicants reach account status', () => {
  assert.equal(resolvePostAuthRoute('WELCOME'), '/welcome');
  assert.equal(resolvePostAuthRoute('DASHBOARD'), '/welcome');
  assert.equal(resolvePostAuthRoute('RESTRICTED'), '/register?step=4');
});
