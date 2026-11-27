export const messages = Object.freeze({
  accountEmailMissing: 'No account found. Check your email or create an account.',
  accountMobileMissing: 'No account found. Check your mobile number or create an account.',
  name: 'Please enter your full name using letters, spaces, hyphens or apostrophes.',
  email: 'Please enter a valid email address, such as name@gmail.com.',
  mobile: 'Please enter a valid 10-digit Indian mobile number.',
  identifier: 'Please enter your email address or 10-digit Indian mobile number.',
  provider: 'Please use Gmail, Yahoo, Outlook or another supported email address.',
  disposable: 'Please use a permanent email address so you can access your account later.',
  retry: 'Something went wrong. Please try again.',
});
const codes = Object.freeze({
  EMAIL_PROVIDER_NOT_SUPPORTED: messages.provider, UNSUPPORTED_EMAIL_PROVIDER: messages.provider,
  DISPOSABLE_EMAIL_NOT_ALLOWED: messages.disposable,
  INVALID_MOBILE_NUMBER: messages.mobile,
  VALIDATION_FAILED: 'Please check the highlighted fields and try again.',
  INVALID_REQUEST: 'Please check your details and try again.',
  INVALID_OTP: 'That code does not match. Please check it and try again.',
  INVALID_OR_EXPIRED_OTP: 'That code is incorrect or has expired. Please try again or request a new code.',
  OTP_EXPIRED: 'That code has expired. Please request a new code.',
  OTP_ALREADY_USED: 'That code has already been used. Please request a new code.',
  OTP_NOT_SENT: 'Please request a code before verifying.',
  OTP_ATTEMPTS_EXCEEDED: 'Too many incorrect attempts. Please request a new code when the timer ends.',
  VERIFICATION_DELIVERY_UNAVAILABLE: 'We cannot send a code right now. Please try again shortly.',
  EMAIL_DOMAIN_CHECK_UNAVAILABLE: 'We cannot check this email address right now. Please try again shortly.',
  EMAIL_ALREADY_REGISTERED: 'This email is already in use. Please log in or use another email.',
  MOBILE_NUMBER_ALREADY_IN_USE: 'This mobile number is already in use. Please log in or use another number.',
  CONTACT_REAUTHENTICATION_REQUIRED: 'Please verify your identity before changing this contact.',
  CONTACT_UPDATE_CONFLICT: 'This contact could not be updated. Please check it and try again.',
  VERIFICATION_REQUIRED: 'Please verify your email or mobile number to continue.',
  MOBILE_VERIFICATION_REQUIRED: 'Please verify your mobile number to continue.',
  GOOGLE_AUTH_UNAVAILABLE: 'Google sign-in is unavailable right now. Please try again or use an email or mobile number.',
  GOOGLE_IDENTITY_CONFLICT: 'We could not link this Google account. Please sign in using your existing method.',
  PAYMENT_GATEWAY_ERROR: 'We could not complete the payment. Please check your payment status before trying again.',
  INVALID_PAYMENT_SIGNATURE: 'We could not confirm your payment. Please check your payment status before trying again.',
  PAYMENT_ALREADY_COMPLETED: 'Your payment is already complete. Please refresh to view your membership.',
  PAYMENT_ORDER_NOT_FOUND: 'We could not find this payment. Please refresh and try again.',
  UNAUTHENTICATED: 'Please log in again to continue.',
  INVALID_DOCUMENT: 'Please choose a supported document and check its size.',
  DOCUMENT_CONSENT_REQUIRED: 'Please give your consent to upload this document.',
});
export function customerError(data = {}, status) {
  if (codes[data.code]) return codes[data.code];
  if (status === 429) return 'Please wait until the timer ends before trying again.';
  if (status === 401) return 'Please log in again to continue.';
  if (status === 403) return 'You cannot complete this action right now. Please sign in again or contact support.';
  if (status >= 500) return 'We cannot complete this right now. Please try again shortly.';
  return messages.retry;
}
const knownFieldMessages = {
  'This email provider is not supported for new accounts. Use a supported provider.': messages.provider,
  'Disposable email services are not supported. Use a permanent email address.': messages.disposable,
};
export function safeErrorData(data, status) {
  const source = data && typeof data === 'object' && !Array.isArray(data) ? data : {};
  const message = customerError(source, status);
  return { ...source, detail: message, message, errors: Array.isArray(source.errors) ? source.errors.map(error => ({
    ...error, message: knownFieldMessages[error.message] || (/email/i.test(error.field) ? messages.email : /mobile|phone/i.test(error.field) ? messages.mobile : /name/i.test(error.field) ? messages.name : 'Please check this field and try again.'),
  })) : undefined };
}

