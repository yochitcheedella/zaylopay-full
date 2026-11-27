import test from 'node:test';
import assert from 'node:assert/strict';
import { applyLoginCooldown, loginRequestCooldown, retryDeadline } from '../src/utils/loginCooldown.js';

const empty = () => ({ identifiers: {}, ipDeadline: 0 });
test('authoritative retry timestamps survive client/server clock skew', () => {
  const now = 1000000;
  assert.equal(retryDeadline({ serverTime: '2026-11-26T10:00:00Z', retryAvailableAt: '2026-11-26T10:00:26Z' }, {}, now), now + 26000);
});
test('identifier countdown decreases, follows normalized identity and survives editing away and back', () => {
  const state = applyLoginCooldown(empty(), 'gopi@gm.com', { retryAfterSeconds: 26, rateLimitScope: 'IDENTIFIER' }, {}, 1000);
  assert.equal(loginRequestCooldown(state, 'gopi@GM.COM', 1000), 26);
  assert.equal(loginRequestCooldown(state, 'other@gm.com', 1000), 0);
  assert.equal(loginRequestCooldown(state, 'gopi@gm.com', 2000), 25);
  assert.equal(loginRequestCooldown(state, 'gopi@gm.com', 27000), 0);
});
test('IP limits continue across identifier changes', () => {
  const state = applyLoginCooldown(empty(), 'gopi@gm.com', { retryAfterSeconds: 90, rateLimitScope: 'IP' }, {}, 1000);
  assert.equal(loginRequestCooldown(state, 'unrelated@gmail.com', 1000), 90);
});
test('distinct case-sensitive email identities and canonical Indian mobiles have correct scopes', () => {
  const state = applyLoginCooldown(empty(), 'Member@gmail.com', { retryAfterSeconds: 60 }, {}, 1000);
  assert.equal(loginRequestCooldown(state, 'member@gmail.com', 1000), 0);
  const mobile = applyLoginCooldown(empty(), '9876543210', { retryAfterSeconds: 60 }, {}, 1000);
  assert.equal(loginRequestCooldown(mobile, '+91 98765 43210', 1000), 60);
});
test('Retry-After fallback and normal issuance use the same countdown', () => {
  assert.equal(retryDeadline({}, { 'retry-after': '26' }, 1000), 27000);
  assert.equal(retryDeadline({ serverTime: '2026-11-26T10:00:00Z', resendAvailableAt: '2026-11-26T10:01:00Z' }, {}, 1000), 61000);
});
