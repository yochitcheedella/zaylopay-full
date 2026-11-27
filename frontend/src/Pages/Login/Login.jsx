import { messages } from '../../utils/messages';
import React, { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Mail, Smartphone, User } from 'lucide-react';
import API from '../../Services/api';
import AuthLayout from '../../Components/Auth/AuthLayout';
import {
  AuthInput,
  AuthButton,
  AuthError,
  LoadingState,
  StatusMessage,
} from '../../Components/Auth/AuthComponents';
import GoogleAuthButton from '../../Components/Auth/GoogleAuthButton';
import GoogleAccountCard from '../../Components/Auth/GoogleAccountCard';
import AccountNotFound from '../../Components/Auth/AccountNotFound';
import AuthDivider from '../../Components/Auth/AuthDivider';
import OtpInput from '../Register/components/OtpInput';
import {
  getEmailDomainSuggestion,
  maskLoginIdentifier,
  normalizeLoginIdentifier,
  validateLoginIdentifier,
  validateEmail,
} from '../../utils/validation';
import { resolvePostAuthRoute } from '../../utils/authRedirect';
import { applyLoginCooldown, loginRequestCooldown } from '../../utils/loginCooldown';
import './Login.css';

function formatCountdown(seconds) {
  const minutes = Math.floor(seconds / 60).toString().padStart(2, '0');
  const remainder = (seconds % 60).toString().padStart(2, '0');
  return `${minutes}:${remainder}`;
}

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();

  // Manual OTP login state
  const [identifier, setIdentifier] = useState('');
  const [identifierTouched, setIdentifierTouched] = useState(false);
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [otpChallenge, setOtpChallenge] = useState(null);
  const [otpDigits, setOtpDigits] = useState(Array(6).fill(''));
  const [otpNotice, setOtpNotice] = useState('');
  const [otpVerifiedSuccess, setOtpVerifiedSuccess] = useState(false);
  const [formError, setFormError] = useState('');
  const [rejectedIdentifier, setRejectedIdentifier] = useState(null);
  const [requestFieldError, setRequestFieldError] = useState(false);
  const [loading, setLoading] = useState(false);
  const [recoveryMode, setRecoveryMode] = useState(false);
  const [requestCooldowns, setRequestCooldowns] = useState({ identifiers: {}, ipDeadline: 0 });
  const identifierInputRef = useRef(null);
  const requestSeqRef = useRef(0);
  const submitLockRef = useRef(false);

  // Google & session state
  const [googleLoading, setGoogleLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);
  const [activeUser, setActiveUser] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const [notFoundState, setNotFoundState] = useState(null); // { identifier, identifierType, pictureUrl }

  // Server-backed Google account linking state
  const [linkingState, setLinkingState] = useState(null); // { email, linkingChannel, maskedTarget, pictureUrl }
  const [linkingChallengeSent, setLinkingChallengeSent] = useState(false);
  const [linkingOtpDigits, setLinkingOtpDigits] = useState(Array(6).fill(''));
  const [linkingCooldownAt, setLinkingCooldownAt] = useState(0);
  const [linkingNotice, setLinkingNotice] = useState('');
  const [linkingLoading, setLinkingLoading] = useState(false);

  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    let isMounted = true;
    async function checkCurrentSession() {
      try {
        const response = await API.get('/auth/session');
        if (!isMounted) return;
        const sessionData = response?.data;

        if (sessionData?.authenticated && sessionData?.sessionState === 'ACTIVE') {
          setActiveUser(sessionData);
          return;
        }

        setActiveUser(null);
        if (sessionData?.onboarding?.active) {
          if (sessionData.onboarding.flowType === 'GOOGLE_COMPLETE_PROFILE') {
            navigate('/register', { replace: true });
            return;
          }
          if (sessionData.onboarding.flowType === 'GOOGLE_LINK_ACCOUNT') {
            try {
              const onboardingRes = await API.get('/auth/google/onboarding');
              const data = onboardingRes.data;
              if (isMounted && data?.status === 'LINKING_CONFIRMATION_REQUIRED') {
                setLinkingState({
                  email: data.email,
                  linkingChannel: data.linkingChannel,
                  maskedTarget: data.maskedMobileNumber || data.maskedEmailAddress,
                  pictureUrl: data.pictureUrl || sessionData.onboarding.pictureUrl || null,
                });
              }
            } catch {
              // Falls back cleanly to login
            }
          }
        }
      } catch {
        if (isMounted) {
          setActiveUser(null);
        }
      } finally {
        if (isMounted) {
          setCheckingSession(false);
        }
      }
    }

    checkCurrentSession();
    return () => {
      isMounted = false;
    };
  }, [navigate]);

  const trimmedIdentifier = identifier.trim();
  const liveIdentifierError = recoveryMode ? validateEmail(identifier) : validateLoginIdentifier(identifier);
  const identifierFieldError = (requestFieldError ? formError : '') || ((identifierTouched || submitAttempted) && liveIdentifierError ? liveIdentifierError : '');
  const identifierSuggestion =
    (identifierTouched || submitAttempted) && trimmedIdentifier.includes('@')
      ? getEmailDomainSuggestion(identifier)
      : null;

  const dynamicLeadingIcon = trimmedIdentifier.includes('@') ? (
    <Mail size={18} strokeWidth={1.9} />
  ) : /^[\d+]/.test(trimmedIdentifier) ? (
    <Smartphone size={18} strokeWidth={1.9} />
  ) : (
    <User size={18} strokeWidth={1.9} />
  );

  const routeAfterAuth = (eligibleStep, options = {}) => {
    const targetRoute = resolvePostAuthRoute(eligibleStep);
    if (eligibleStep) {
      navigate(targetRoute, {
        replace: true,
        state: options.justAuthenticated ? { justAuthenticated: true } : undefined,
      });
      return;
    }
    const from = location.state?.from?.pathname;
    if (from && from !== '/login') {
      navigate(from, { replace: true });
      return;
    }
    navigate('/register', { replace: true });
  };

  const resetToFreshLogin = (clearIdentifier = false) => {
    setRejectedIdentifier(null);
    setRequestFieldError(false);
    requestSeqRef.current += 1;
    submitLockRef.current = false;
    setNotFoundState(null);
    setOtpChallenge(null);
    setOtpDigits(Array(6).fill(''));
    setOtpNotice('');
    setOtpVerifiedSuccess(false);
    setLinkingState(null);
    setLinkingChallengeSent(false);
    setLinkingOtpDigits(Array(6).fill(''));
    setLinkingNotice('');
    setFormError('');
    setSubmitAttempted(false);
    if (clearIdentifier) {
      setIdentifier('');
      setIdentifierTouched(false);
      setTimeout(() => identifierInputRef.current?.focus(), 0);
    }
  };

  const handleRequestOtp = async (e) => {
    e?.preventDefault();
    if (loading || googleLoading || submitLockRef.current
      || loginRequestCooldown(requestCooldowns, identifier) > 0) return;

    setSubmitAttempted(true);
    const validationError = recoveryMode ? validateEmail(identifier) : validateLoginIdentifier(identifier);
    if (validationError) {
      identifierInputRef.current?.focus();
      return;
    }

    if (!recoveryMode && rejectedIdentifier === (normalizeLoginIdentifier(identifier) || identifier.trim())) return;

    const requestSeq = ++requestSeqRef.current;
    submitLockRef.current = true;
    setLoading(true);
    setFormError('');
    setOtpNotice('');
    setOtpVerifiedSuccess(false);

    try {
      const cleanIdentifier = normalizeLoginIdentifier(identifier) || identifier.trim();
      const response = await API.post(recoveryMode ? '/auth/recovery/otp/request' : '/auth/login/otp/request', {
        identifier: cleanIdentifier,
      });
      if (requestSeq !== requestSeqRef.current) return;

      const data = response.data;
      if (!recoveryMode && data.outcome && data.outcome !== 'CHALLENGE_INITIATED') {
        setOtpChallenge(null);
        setRequestFieldError(true);
        if (data.outcome === 'ACCOUNT_NOT_FOUND') setRejectedIdentifier(cleanIdentifier);
        const contact = cleanIdentifier.includes('@') ? 'email address' : 'mobile number';
        setFormError(data.outcome === 'ACCOUNT_NOT_FOUND'
          ? (contact === 'email address' ? messages.accountEmailMissing : messages.accountMobileMissing)
          : data.outcome === 'CONTACT_VERIFICATION_REQUIRED'
            ? 'This contact needs verification. Please recover your saved registration or contact support.'
            : data.outcome === 'REQUEST_LIMITED'
              ? 'Please wait before requesting another code.'
              : 'We cannot sign you in with this account. Please contact support.');
        return;
      }
      if (data.accepted !== true || typeof data.challengeId !== 'string' || !data.challengeId.trim()) {
        setFormError("We couldn't complete your request. Please try again shortly.");
        return;
      }
      const nowMs = Date.now();
      setNow(nowMs);
      setRequestCooldowns(current => applyLoginCooldown(current, cleanIdentifier, data, {}, nowMs));
      const serverTimeMs = Date.parse(data.serverTime);
      const serverOffsetMs = Number.isFinite(serverTimeMs) ? serverTimeMs - nowMs : 0;
      const expiresAt = Date.parse(data.expiresAt);
      const resendAt = Date.parse(data.resendAvailableAt);
      setOtpChallenge({
        challengeId: data.challengeId,
        recovery: recoveryMode,
        identifier: cleanIdentifier,
        maskedDestination: maskLoginIdentifier(cleanIdentifier),
        serverOffsetMs,
        resendAt: Number.isFinite(resendAt)
          ? resendAt
          : nowMs + serverOffsetMs + Number(data.resendAfterSeconds || 60) * 1000,
        expiresAt: Number.isFinite(expiresAt)
          ? expiresAt
          : nowMs + serverOffsetMs + Number(data.expiresInSeconds || 300) * 1000,
      });
      setOtpDigits(Array(6).fill(''));
      setOtpNotice(
        data.message ||
          'If an eligible account matches, a 6-digit verification code has been sent.'
      );
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return;
      const status = err.response?.status;
      const code = err.response?.data?.code;
      const retryAfter = Number(err.response?.data?.retryAfterSeconds || 60);
      if (status === 429) {
        const responseNow = Date.now();
        setNow(responseNow);
        setRequestCooldowns(current => applyLoginCooldown(current, identifier, err.response?.data || {}, err.response?.headers || {}, responseNow));
        if (otpChallenge) {
          setOtpChallenge((current) =>
            current
              ? {
                  ...current,
                  resendAt: Date.now() + current.serverOffsetMs + retryAfter * 1000,
                }
              : current
          );
        }
        setFormError('');
      } else if (status === 400) {
        setRequestFieldError(true);
        const backendError = err.response?.data?.errors?.[0]?.message;
        setFormError(
          backendError ||
            err.response?.data?.detail ||
            'Enter a valid 10-digit mobile number or email address.'
        );
      } else if (
        status === 503 ||
        code === 'DATABASE_UNAVAILABLE' ||
        code === 'VERIFICATION_DELIVERY_UNAVAILABLE'
      ) {
        setFormError(
          err.response?.data?.detail ||
            'Verification service is temporarily unavailable. Your input is preserved — please retry shortly.'
        );
      } else if (!err.response) {
        setFormError(
          'Unable to reach ZayloPe servers. Check your internet connection and try again.'
        );
      } else {
        setFormError(
          err.response?.data?.detail ||
            'Unable to send verification code right now. Please try again.'
        );
      }
    } finally {
      submitLockRef.current = false;
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
      }
    }
  };

  const handleVerifyOtp = async (e) => {
    e.preventDefault();
    if (loading || submitLockRef.current || !otpChallenge) return;

    const code = otpDigits.join('');
    if (code.length !== 6 || otpDigits.some((d) => !/^\d$/.test(d))) {
      setFormError('Enter the 6-digit verification code.');
      return;
    }

    const requestSeq = ++requestSeqRef.current;
    submitLockRef.current = true;
    setLoading(true);
    setFormError('');

    try {
      const response = await API.post(otpChallenge.recovery ? '/auth/recovery/otp/verify' : '/auth/login/otp/verify', {
        challengeId: otpChallenge.challengeId,
        identifier: otpChallenge.identifier,
        code,
      });
      if (requestSeq !== requestSeqRef.current) return;

      setOtpVerifiedSuccess(true);
      setTimeout(() => {
        routeAfterAuth(response.data?.eligibleStep, { justAuthenticated: true });
      }, 300);
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return;
      const status = err.response?.status;
      const codeName = err.response?.data?.code;
      if (status === 429 || codeName === 'OTP_ATTEMPTS_EXCEEDED') {
        setFormError('Too many incorrect attempts. Request a new code after the timer ends.');
      } else if (status === 410 || codeName === 'OTP_EXPIRED') {
        setFormError('This verification code has expired. Request a new code below.');
      } else if (status === 503 || codeName === 'DATABASE_UNAVAILABLE') {
        setFormError('Authentication service is temporarily unavailable. Please retry.');
      } else if (status === 401) {
        setOtpChallenge(null);
        setOtpDigits(Array(6).fill(''));
        setFormError('Your verification request expired. Request a new code to continue.');
      } else if (!err.response) {
        setFormError('Network error while verifying your code. Please retry.');
      } else {
        setFormError(
          'That code is incorrect or expired. Check it and try again, or request a new code.'
        );
      }
    } finally {
      submitLockRef.current = false;
      if (requestSeq === requestSeqRef.current) {
        setLoading(false);
      }
    }
  };

  const handleGoogleSuccess = async (credential) => {
    if (googleLoading || loading) return;
    const requestSeq = ++requestSeqRef.current;
    setGoogleLoading(true);
    setFormError('');
    setNotFoundState(null);

    try {
      const response = await API.post('/auth/google', { credential, intent: 'LOGIN' });
      if (requestSeq !== requestSeqRef.current) return;
      const data = response.data;

      if (data.status === 'AUTHENTICATED') {
        routeAfterAuth(data.eligibleStep, { justAuthenticated: true });
      } else if (data.status === 'ACCOUNT_NOT_FOUND') {
        setNotFoundState({
          identifier: data.email || '',
          identifierType: 'email',
          pictureUrl: data.pictureUrl || null,
        });
      } else if (data.status === 'NEEDS_PROFILE_COMPLETION') {
        navigate('/register', { replace: true });
      } else if (data.status === 'LINKING_CONFIRMATION_REQUIRED') {
        setLinkingState({
          email: data.email,
          linkingChannel: data.linkingChannel,
          maskedTarget: data.maskedMobileNumber || data.maskedEmailAddress,
          pictureUrl: data.pictureUrl || null,
        });
        setLinkingChallengeSent(false);
        setLinkingOtpDigits(Array(6).fill(''));
        setLinkingNotice('');
      }
    } catch (err) {
      if (requestSeq !== requestSeqRef.current) return;
      const status = err.response?.status;
      const code = err.response?.data?.code;
      if (status === 503 || code === 'GOOGLE_AUTH_UNAVAILABLE') {
        setFormError(
          'Google sign-in is temporarily unavailable. You can still log in with your mobile or email code below.'
        );
      } else if (status === 401 || code === 'INVALID_GOOGLE_TOKEN' || code === 'GOOGLE_EMAIL_UNVERIFIED') {
        setFormError(
          err.response?.data?.detail ||
            'We could not verify your Google account. Please sign in with Google again or use mobile/email OTP.'
        );
      } else if (code === 'GOOGLE_IDENTITY_CONFLICT') {
        setFormError(
          err.response?.data?.detail ||
            'This account is already linked to another Google profile.'
        );
      } else if (!err.response) {
        setFormError('Unable to reach ZayloPe servers. Please check your internet connection.');
      } else {
        setFormError(
          err.response?.data?.detail ||
            'Google sign-in could not be completed. Please try again or use your mobile or email.'
        );
      }
    } finally {
      if (requestSeq === requestSeqRef.current) {
        setGoogleLoading(false);
      }
    }
  };

  const handleSendLinkingOtp = async () => {
    if (linkingLoading) return;
    setLinkingLoading(true);
    setFormError('');
    setLinkingNotice('');

    try {
      const res = await API.post('/auth/google/link/send-otp', {});
      const data = res.data;
      setLinkingChallengeSent(true);
      setLinkingOtpDigits(Array(6).fill(''));
      setLinkingCooldownAt(Date.now() + Number(data.resendAfterSeconds || 60) * 1000);
      setLinkingNotice(
        data.deliveryMode === 'MOCK'
          ? `Test verification code generated for ${data.maskedDestination}. Check backend terminal logs.`
          : `A 6-digit verification code was sent to ${data.maskedDestination}.`
      );
    } catch (err) {
      const retry = Number(err.response?.data?.retryAfterSeconds || 0);
      if (retry > 0) {
        setLinkingCooldownAt(Date.now() + retry * 1000);
      }
      if (err.response?.status === 401) {
        setLinkingState(null);
        setFormError('Your Google sign-in timed out. Please continue with Google again.');
      } else {
        setFormError(
          err.response?.data?.detail || 'Could not send verification code. Please try again.'
        );
      }
    } finally {
      setLinkingLoading(false);
    }
  };

  const handleConfirmLinking = async (e) => {
    e.preventDefault();
    const code = linkingOtpDigits.join('');
    if (linkingLoading || code.length !== 6) return;

    setLinkingLoading(true);
    setFormError('');

    try {
      const res = await API.post('/auth/google/link/verify', { otpCode: code });
      routeAfterAuth(res.data?.eligibleStep, { justAuthenticated: true });
    } catch (err) {
      if (err.response?.status === 401) {
        setLinkingState(null);
        setFormError('Your Google sign-in timed out. Please continue with Google again.');
      } else {
        setFormError(
          err.response?.data?.detail ||
            'Invalid or expired verification code. Please check and try again.'
        );
      }
    } finally {
      setLinkingLoading(false);
    }
  };

  const handleCancelLinking = async () => {
    setLinkingLoading(true);
    try {
      await API.post('/auth/google/cancel', {});
    } catch {
      // Best-effort clear
    } finally {
      setLinkingState(null);
      setLinkingChallengeSent(false);
      setLinkingOtpDigits(Array(6).fill(''));
      setLinkingNotice('');
      setFormError('');
      setLinkingLoading(false);
    }
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await API.post('/auth/logout');
      sessionStorage.removeItem('zaylopeToken');
      sessionStorage.removeItem('zaylopeUserName');
      sessionStorage.removeItem('zaylopeEmail');
      setActiveUser(null);
      resetToFreshLogin(true);
    } catch {
      setFormError('Unable to sign out. Please check your connection and try again.');
    } finally {
      setLoggingOut(false);
    }
  };

  const otpResendCooldown = otpChallenge
    ? Math.max(loginRequestCooldown(requestCooldowns, otpChallenge.identifier, now),
      Math.max(0, Math.ceil((otpChallenge.resendAt - (now + otpChallenge.serverOffsetMs)) / 1000)))
    : 0;
  const requestCooldown = loginRequestCooldown(requestCooldowns, identifier, now);
  const otpExpiresIn = otpChallenge
    ? Math.max(0, Math.ceil((otpChallenge.expiresAt - (now + otpChallenge.serverOffsetMs)) / 1000))
    : 0;
  const isOtpExpired = Boolean(otpChallenge && otpExpiresIn === 0);
  const linkingCooldown = Math.max(0, Math.ceil((linkingCooldownAt - now) / 1000));
  const activeIdentityAuthenticated = Boolean(activeUser && (activeUser.identityAuthenticated
    ?? (activeUser.sessionScope === 'AUTHENTICATED')));

  let cardTitle = recoveryMode ? 'Recover your registration' : 'Welcome back!';
  let cardSubtitle = recoveryMode
    ? 'Prove ownership of your saved email to restore access.'
    : 'Log in to your ZayloPe account.';

  if (notFoundState) {
    cardTitle = null;
    cardSubtitle = null;
  } else if (linkingState) {
    cardTitle = 'Confirm your existing account';
    cardSubtitle = 'Verify ownership before connecting Google to your account.';
  } else if (activeUser) {
    cardTitle = activeIdentityAuthenticated ? 'Welcome back!' : 'Saved registration';
    cardSubtitle = activeIdentityAuthenticated ? 'You are signed in and ready to continue.' : 'Verify a contact to continue your registration.';
  } else if (otpChallenge) {
    cardTitle = otpChallenge.recovery ? 'Verify your recovery' : 'Verify your login';
    cardSubtitle = 'Enter your six-digit verification code.';
  }

  return (
    <AuthLayout
      cardSize="compact"
      layoutVariant="login"
      title={cardTitle}
      subtitle={cardSubtitle}
    >
      {checkingSession ? (
        <LoadingState message="Checking account status…" />
      ) : activeUser ? (
        <div className="zp-active-session-card">
          {formError && <AuthError error={formError} onDismiss={() => setFormError('')} />}
          <div className="zp-session-user-avatar" aria-hidden="true">
            {(activeUser.fullName || 'Z').charAt(0).toUpperCase()}
          </div>
          <p className="zp-session-welcome-msg">
            {activeIdentityAuthenticated ? 'Signed in as ' : 'Saved registration for '}
            <strong className="zp-session-user-name">
              {activeUser.fullName || 'Applicant'}
            </strong>
          </p>
          <StatusMessage kind={activeUser.eligibleStep === 'RESTRICTED' ? 'error' : activeIdentityAuthenticated ? 'success' : 'info'}>
            {activeUser.eligibleStep === 'RESTRICTED'
              ? 'This account currently has a restriction notice.'
              : !activeIdentityAuthenticated
                ? 'Your draft is saved. Contact ownership has not been verified.'
                : activeUser.canAccessDashboard
                ? 'Your ZayloPe lifetime membership is active.'
                : 'Your ZayloPe account session is active.'}
          </StatusMessage>
          <div className="zp-session-actions">
            <AuthButton
              variant="primary"
              onClick={() => routeAfterAuth(activeUser.eligibleStep)}
            >
              {activeUser.eligibleStep === 'RESTRICTED'
                ? 'View Account Status'
                : activeUser.canAccessDashboard
                  ? 'Open Membership Card'
                  : 'Continue Application'}
            </AuthButton>
            <AuthButton
              variant="secondary"
              loading={loggingOut}
              disabled={loggingOut}
              onClick={handleLogout}
            >
              Sign out / Use another account
            </AuthButton>
          </div>
        </div>
      ) : notFoundState ? (
        <AccountNotFound
          identifier={notFoundState.identifier}
          identifierType={notFoundState.identifierType}
          pictureUrl={notFoundState.pictureUrl}
          onCreateAccount={() => navigate('/register')}
          onTryAnother={() => resetToFreshLogin(true)}
          onBackToLogin={() => resetToFreshLogin(false)}
        />
      ) : linkingState ? (
        <form onSubmit={handleConfirmLinking} className="zp-linking-confirm-box zp-form-column-wide" noValidate>
          <GoogleAccountCard
            email={linkingState.email}
            pictureUrl={linkingState.pictureUrl}
            badgeMode="linking"
            onUseDifferentAccount={handleCancelLinking}
            disabled={linkingLoading}
          />

          <div className="zp-linking-info">
            <p className="zp-linking-desc">
              An existing ZayloPe account matches this email. Confirm ownership with a 6-digit
              verification code sent to{' '}
              <strong>{linkingState.maskedTarget || 'your verified contact'}</strong>.
            </p>
          </div>

          {linkingNotice && <StatusMessage kind="success">{linkingNotice}</StatusMessage>}
          {formError && <AuthError error={formError} onDismiss={() => setFormError('')} />}

          {!linkingChallengeSent ? (
            <div className="zp-linking-actions">
              <AuthButton
                type="button"
                variant="primary"
                loading={linkingLoading}
                onClick={handleSendLinkingOtp}
              >
                Send verification code
              </AuthButton>
            </div>
          ) : (
            <>
              <div className="zp-login-otp-wrap">
                <OtpInput
                  channel={linkingState.linkingChannel === 'EMAIL' ? 'Email' : 'Mobile'}
                  value={linkingOtpDigits}
                  onChange={setLinkingOtpDigits}
                  disabled={linkingLoading}
                  hasError={Boolean(formError)}
                />
              </div>

              <div className="zp-linking-actions">
                <AuthButton
                  type="submit"
                  variant="primary"
                  loading={linkingLoading}
                  disabled={linkingLoading || linkingOtpDigits.some((d) => !/^\d$/.test(d))}
                >
                  Confirm &amp; Continue
                </AuthButton>
                <AuthButton
                  type="button"
                  variant="secondary"
                  disabled={linkingLoading || linkingCooldown > 0}
                  onClick={handleSendLinkingOtp}
                >
                  {linkingCooldown > 0 ? `Resend in ${linkingCooldown}s` : 'Resend code'}
                </AuthButton>
              </div>
            </>
          )}
        </form>
      ) : otpChallenge ? (
        <form onSubmit={handleVerifyOtp} className="zp-linking-confirm-box zp-form-column" noValidate>
          {otpNotice && <p className="zp-login-otp-notice" role="status">{otpNotice}</p>}
          {formError && <AuthError error={formError} onDismiss={() => setFormError('')} />}

          <div className="zp-login-otp-destination">
            <span>Verification code destination</span>
            <strong>{otpChallenge.maskedDestination}</strong>
          </div>

          <div className="zp-login-otp-wrap">
            <OtpInput
              channel={otpChallenge.identifier.includes('@') ? 'Email' : 'Mobile'}
              value={otpDigits}
              onChange={(digits) => {
                setOtpDigits(digits);
                if (formError) setFormError('');
              }}
              disabled={loading || isOtpExpired}
              hasError={Boolean(formError)}
              isSuccess={otpVerifiedSuccess}
            />
            {isOtpExpired ? (
              <p className="zp-otp-expired-note" role="status">
                Code expired. Request a new code below.
              </p>
            ) : (
              <p className="zp-otp-timer-note" aria-live="off">
                Code expires in {formatCountdown(otpExpiresIn)}
              </p>
            )}
          </div>

          <div className="zp-linking-actions">
            <AuthButton
              type="submit"
              variant="primary"
              loading={loading}
              disabled={
                loading ||
                isOtpExpired ||
                otpDigits.some((d) => !/^\d$/.test(d))
              }
            >
              {otpChallenge.recovery ? 'Verify & Continue' : 'Verify & Log in'}
            </AuthButton>
            <AuthButton
              type="button"
              variant="secondary"
              disabled={loading || otpResendCooldown > 0}
              onClick={handleRequestOtp}
            >
              {otpResendCooldown > 0
                ? `Resend in ${formatCountdown(otpResendCooldown)}`
                : 'Resend code'}
            </AuthButton>
            <button
              type="button"
              className="zp-login-text-btn"
              disabled={loading}
              onClick={() => {
                resetToFreshLogin(false);
                setTimeout(() => identifierInputRef.current?.focus(), 0);
              }}
            >
              Change details
            </button>
            <p className="zp-login-create-account">
              No code yet? Check spam or SMS messages and confirm your details. Only eligible registered contacts receive a code. You can also{' '}
              <Link to="/register" className="zp-login-switch-link">
                create an account
              </Link>
              .
            </p>
          </div>
        </form>
      ) : (
        <div className="zp-form-column">

          <GoogleAuthButton
            onSuccess={handleGoogleSuccess}
            onError={(msg) =>
              setFormError(msg || 'Google sign-in could not be completed. Please try again.')
            }
            loading={googleLoading}
            text="Continue with Google"
            disabled={loading}
          />

          <AuthDivider text="Or continue with" />

          <form onSubmit={handleRequestOtp} noValidate>
            <AuthInput
              id="login-identifier"
              name="identifier"
              maxLength={254}
              label={recoveryMode ? 'Saved email address' : 'Mobile number or email address'}
              type="text"
              leadingIcon={dynamicLeadingIcon}
              inputMode={recoveryMode || !/^[+\d -]+$/.test(trimmedIdentifier) ? 'email' : 'tel'}
              inputRef={identifierInputRef}
              value={identifier}
              onChange={(e) => {
                requestSeqRef.current += 1;
                setIdentifier(e.target.value);
                setRejectedIdentifier(null);
    setRequestFieldError(false);
                if (formError) setFormError('');
                if (submitAttempted) setSubmitAttempted(false);
              }}
              onBlur={() => setIdentifierTouched(true)}
              placeholder="e.g. 9876543210 or name@example.com"
              autoComplete="username"
              required
              disabled={loading || googleLoading}
              error={identifierFieldError}
              suggestion={identifierSuggestion}
              onApplySuggestion={(suggestion) => {
                if (loading || googleLoading) return;
                setIdentifier(suggestion);
                setRejectedIdentifier(null);
    setRequestFieldError(false);
                setIdentifierTouched(true);
                setFormError('');
              }}
              helperText={loading ? 'Checking your account…' : undefined}
            />



            {formError && !requestFieldError && <p className="zp-login-request-error" role="alert">{formError}</p>}
            <div className="zp-login-actions">
              <AuthButton
                type="submit"
                variant="primary"
                loading={loading}
                disabled={loading || googleLoading || requestCooldown > 0 || rejectedIdentifier === (normalizeLoginIdentifier(identifier) || identifier.trim())}
              >
                {requestCooldown > 0 ? `Try again in ${formatCountdown(requestCooldown)}`
                  : recoveryMode ? 'Get recovery code' : 'Get verification code'}
              </AuthButton>
              {requestCooldown > 0 && <p className="zp-login-request-note" role="status">
                You can request another code when the timer ends.
              </p>}
            </div>
          </form>


          <p className="zp-login-switch-prompt">
            New to ZayloPe?{' '}
            <Link to="/register" className="zp-login-switch-link">
              Create an account
            </Link>
          </p>
          <button type="button" className="zp-login-text-btn" disabled={loading || googleLoading}
            onClick={() => { resetToFreshLogin(false); setRecoveryMode(current => !current); }}>
            {recoveryMode ? 'Back to login' : 'Resume unfinished registration'}
          </button>

        </div>
      )}
    </AuthLayout>
  );
}

