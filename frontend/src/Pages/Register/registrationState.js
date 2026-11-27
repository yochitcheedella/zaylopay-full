export function restorationNotice(failure, online = true) {
  if (!online) {
    return {
      kind: 'offline',
      message: 'You are offline. Reconnect to check for a saved application. Your entries stay on this page.',
      retry: true,
    };
  }
  const status = failure.response?.status;
  const code = failure.response?.data?.code;
  if (status === 401) {
    if (code === 'UNAUTHENTICATED' && failure.response?.data?.sessionState === 'NONE') {
      return null;
    }
    return {
      kind: 'session',
      message: 'Your saved progress could not be confirmed for your security. Log in to resume your application.',
      retry: false,
    };
  }
  if (code === 'DATABASE_UNAVAILABLE' || code === 'DATABASE_CONFIGURATION_ERROR' || code === 'DATABASE_OPERATION_FAILED') {
    return {
      kind: 'database',
      message: 'Application storage is temporarily unavailable. Your entries have not been changed. Please retry.',
      retry: true,
    };
  }
  if (!failure.response || status === 500 || status === 502 || status === 503 || status === 504) {
    return {
      kind: 'service',
      message: 'The registration service is temporarily unavailable. You can keep entering your details and retry.',
      retry: true,
    };
  }
  return {
    kind: 'error',
    message: 'We could not check your saved application. Please retry or contact support.',
    retry: true,
  };
}

import {
  validateEmail,
  validateRegistrationEmail,
  validateFullName,
  validateMobile,
} from '../../utils/validation';

export function personalFieldErrors(values, emailPolicy) {
  const errors = {};
  const fullNameError = validateFullName(values.full_name || '');
  const mobileError = validateMobile(values.mobile_number || '');
  const emailError = emailPolicy === undefined
    ? validateEmail(values.email || '')
    : validateRegistrationEmail(values.email || '', emailPolicy);

  if (fullNameError) {
    errors.full_name = fullNameError;
  }
  if (mobileError) {
    errors.mobile_number = mobileError;
  }
  if (emailError) {
    errors.email = emailError;
  }

  return errors;
}

export function maskMobile(mobile) {
  if (!mobile) return '';
  const digits = String(mobile).replace(/\D/g, '');
  if (digits.length >= 10) {
    const lastFour = digits.slice(-4);
    return '+91 ••••• •' + lastFour;
  }
  return mobile;
}

export function maskEmail(email) {
  if (!email || !email.includes('@')) return email || '';
  const [user, domain] = email.split('@');
  if (user.length <= 2) {
    return user[0] + '•••@' + domain;
  }
  return user[0] + '•••' + user.slice(-1) + '@' + domain;
}
