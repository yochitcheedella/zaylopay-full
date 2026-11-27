import { normalizeLoginIdentifier } from './validation.js';

export function retryDeadline(data = {}, headers = {}, now = Date.now()) {
  const serverTime = Date.parse(data.serverTime);
  const retryAt = Date.parse(data.retryAvailableAt || data.resendAvailableAt);
  if (Number.isFinite(serverTime) && Number.isFinite(retryAt)) {
    return now + Math.max(0, retryAt - serverTime);
  }
  const seconds = Number(data.retryAfterSeconds ?? data.resendAfterSeconds ?? headers['retry-after'] ?? headers['Retry-After'] ?? 60);
  return now + Math.max(1, Number.isFinite(seconds) ? seconds : 60) * 1000;
}

export function applyLoginCooldown(current, identifier, data, headers, now = Date.now()) {
  const deadline = retryDeadline(data, headers, now);
  const identifiers = Object.fromEntries(Object.entries(current.identifiers).filter(([, until]) => until > now));
  if (data.rateLimitScope === 'IP') {
    return { identifiers, ipDeadline: Math.max(current.ipDeadline, deadline) };
  }
  const key = normalizeLoginIdentifier(identifier);
  if (key) identifiers[key] = Math.max(identifiers[key] || 0, deadline);
  return { ...current, identifiers };
}

export function loginRequestCooldown(current, identifier, now = Date.now()) {
  const key = normalizeLoginIdentifier(identifier);
  return Math.max(0, Math.ceil((Math.max(current.ipDeadline, current.identifiers[key] || 0) - now) / 1000));
}
