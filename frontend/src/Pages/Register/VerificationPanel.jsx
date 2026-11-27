import { useEffect, useRef, useState } from 'react';
import API from '../../Services/api';
import './VerificationPanel.css';

function OtpDigits({ channel, value, onChange, disabled }) {
  const inputs = useRef([]);

  useEffect(() => {
    inputs.current[0]?.focus();
  }, []);

  const paste = text => {
    const digits = text.replace(/\D/g, '').slice(0, 6);
    if (!digits) return;
    onChange(digits.padEnd(6, ' ').split('').map(digit => digit === ' ' ? '' : digit));
    inputs.current[Math.min(digits.length, 5)]?.focus();
  };

  return (
    <div
      className="verification-digits"
      role="group"
      aria-label={`${channel} six-digit code`}
      onPaste={event => {
        event.preventDefault();
        paste(event.clipboardData.getData('text'));
      }}
    >
      {value.map((digit, index) => (
        <input
          key={index}
          ref={node => { inputs.current[index] = node; }}
          aria-label={`${channel} code digit ${index + 1}`}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={6}
          value={digit}
          disabled={disabled}
          onChange={event => {
            const raw = event.target.value.replace(/\D/g, '');
            if (raw.length > 1) {
              paste(raw);
              return;
            }
            onChange(value.map((current, position) => position === index ? raw : current));
            if (raw && index < 5) inputs.current[index + 1]?.focus();
          }}
          onFocus={event => event.target.select()}
          onKeyDown={event => {
            if (event.key === 'Backspace' && !digit && index > 0) {
              event.preventDefault();
              onChange(value.map((current, position) => position === index - 1 ? '' : current));
              inputs.current[index - 1]?.focus();
            }
            if (event.key === 'ArrowLeft' && index > 0) {
              event.preventDefault();
              inputs.current[index - 1]?.focus();
            }
            if (event.key === 'ArrowRight' && index < 5) {
              event.preventDefault();
              inputs.current[index + 1]?.focus();
            }
          }}
        />
      ))}
    </div>
  );
}

function VerifiedSummary({ title, destination }) {
  return (
    <section className="verification-channel is-verified" aria-label={`${title} verified`}>
      <div className="verification-channel-header">
        <h3>{title}</h3>
        <span className="verification-check">
          <svg aria-hidden="true" viewBox="0 0 20 20">
            <path d="m5 10 3.2 3.2L15.5 6" />
          </svg>
          Verified
        </span>
      </div>
      <p className="verification-destination">{destination}</p>
    </section>
  );
}

function ChannelVerification({ channel, state, deliveryMode, online, blocked, onStatus }) {
  const [digits, setDigits] = useState(Array(6).fill(''));
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [issuedChallenge, setIssuedChallenge] = useState(null);
  const lock = useRef(false);
  const title = channel === 'mobile' ? 'Mobile' : 'Email';

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  const activeState = issuedChallenge ? { ...state, ...issuedChallenge } : state;
  const serverNow = now;
  const resendAt = Date.parse(activeState?.resendAvailableAt) || 0;
  const cooldown = Math.max(0, Math.ceil((Math.max(resendAt, retryAt) - serverNow) / 1000));
  const expiresAt = Date.parse(activeState?.expiresAt) || 0;
  const expired = activeState?.challengeStatus === 'EXPIRED' || (expiresAt > 0 && serverNow >= expiresAt);
  const locked = activeState?.challengeStatus === 'LOCKED';
  const hasChallenge = locked || (expiresAt > 0 && ['ACTIVE', 'EXPIRED'].includes(activeState?.challengeStatus));
  const sendingDisabled = busy !== '' || !online || blocked || !state?.enabled || cooldown > 0;
  const verifyingDisabled = busy !== '' || !online || blocked || !hasChallenge || expired || locked
    || digits.some(digit => !/^\d$/.test(digit));

  const act = async action => {
    if (lock.current || busy || !online || blocked || state?.verified) return;
    if (action === 'send' && (!state?.enabled || cooldown > 0)) return;
    if (action === 'verify' && verifyingDisabled) {
      if (!digits.every(digit => /^\d$/.test(digit))) setError('Enter all six digits.');
      return;
    }

    lock.current = true;
    setBusy(action);
    setError('');
    setMessage('');
    let dispatchResponse = null;
    try {
      if (action === 'send') {
        const response = await API.post(`/applicants/me/verification/${channel}/send`, {});
        dispatchResponse = response.data;
        setNow(Date.now());
        setIssuedChallenge({
          challengeStatus: 'ACTIVE',
          expiresAt: dispatchResponse.expiresAt,
          resendAvailableAt: dispatchResponse.resendAvailableAt,
          serverTime: dispatchResponse.serverTime,
        });
        setDigits(Array(6).fill(''));
        setMessage(deliveryMode === 'MOCK'
          ? 'Development test code generated. Check backend console logs to copy the 6-digit code.'
          : `A verification code was sent to your ${channel}.`);
      } else {
        await API.post(`/applicants/me/verification/${channel}/verify`, { code: digits.join('') });
        setDigits(Array(6).fill(''));
        setMessage(`${title} verified successfully.`);
      }

      const restored = await API.get('/applicants/me/verification/status');
      onStatus(restored.data);
      setIssuedChallenge(null);
    } catch (failure) {
      const data = failure.response?.data;
      const retry = Number(data?.retryAfterSeconds || failure.response?.headers?.['retry-after']);
      if (retry > 0) setRetryAt(now + retry * 1000);
      if (failure.response?.status === 401) onStatus(null);
      let detail = data?.detail || (navigator.onLine
        ? 'Unable to complete verification. Please try again.'
        : 'You are offline. Reconnect to continue.');
      if (action === 'verify' && [410, 429].includes(failure.response?.status)) {
        try {
          const restored = await API.get('/applicants/me/verification/status');
          onStatus(restored.data);
        } catch (refreshFailure) {
          if (refreshFailure.response?.status === 401) onStatus(null);
          detail = `${detail} Verification status could not be refreshed; reload this page before trying again.`;
        }
      }
      setError(dispatchResponse
        ? 'The code was issued, but the latest verification status could not be loaded. You can enter the code or refresh this page before requesting another.'
        : detail);
    } finally {
      lock.current = false;
      setBusy('');
    }
  };

  if (state?.verified) return <VerifiedSummary title={`${title} verification`} destination={state.maskedDestination} />;

  return (
    <section className="verification-channel" aria-busy={busy !== ''}>
      <div className="verification-channel-header">
        <h3>{title} verification</h3>
        <span className="verification-status">Required</span>
      </div>
      <p className="verification-destination">{state?.maskedDestination || 'Loading registered contact…'}</p>
      {!state?.enabled && (
        <div className="verification-unconfigured-notice" role="note">
          <p>SMS/Email code delivery is not configured for this environment. Your registration draft is saved.</p>
        </div>
      )}

      {hasChallenge && !locked && (
        <>
          <p className="verification-instructions" id={`${channel}-otp-instructions`}>
            Enter the six-digit code sent to your {channel}. Never share it with anyone.
          </p>
          <OtpDigits channel={title} value={digits} onChange={setDigits} disabled={!online || busy !== '' || expired || locked} />
          {expired && <p className="verification-hint" role="status">This code has expired. Request a new code to continue.</p>}
          {!expired && !locked && expiresAt > 0 && (
            <p className="verification-hint">Code expires in {Math.max(0, Math.ceil((expiresAt - serverNow) / 1000))} seconds.</p>
          )}
          {locked && <p className="verification-hint" role="status">The attempt limit was reached. Request a new code when the resend timer allows.</p>}
        </>
      )}

      <div className="verification-channel-actions">
        {hasChallenge ? (
          <>
            <button type="button" className="verification-verify" disabled={verifyingDisabled} onClick={() => act('verify')}>
              {busy === 'verify' ? 'Verifying…' : `Verify ${channel}`}
            </button>
            <button type="button" className="verification-resend" disabled={sendingDisabled} onClick={() => act('send')}>
              {busy === 'send' ? 'Sending…' : cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
            </button>
          </>
        ) : (
          <button type="button" className="verification-send" disabled={sendingDisabled} onClick={() => act('send')}>
            {!state?.enabled ? 'Delivery not configured' : busy === 'send' ? 'Sending…' : cooldown > 0 ? `Send in ${cooldown}s` : 'Send code'}
          </button>
        )}
      </div>
      {error && <p className="verification-error" role="alert">{error}</p>}
      {message && <p className="verification-message" role="status">{message}</p>}
    </section>
  );
}

export default function VerificationPanel({ state, onStatus }) {
  const [online, setOnline] = useState(navigator.onLine);
  const [refreshError, setRefreshError] = useState('');
  const mobileVerified = state?.mobile?.verified === true;

  useEffect(() => {
    const offline = () => setOnline(false);
    const onlineAgain = async () => {
      setOnline(true);
      try {
        const response = await API.get('/applicants/me/verification/status');
        onStatus(response.data);
        setRefreshError('');
      } catch (failure) {
        if (failure.response?.status === 401) onStatus(null);
        setRefreshError(failure.response?.data?.detail || 'Unable to restore verification. Please retry.');
      }
    };
    window.addEventListener('offline', offline);
    window.addEventListener('online', onlineAgain);
    return () => {
      window.removeEventListener('offline', offline);
      window.removeEventListener('online', onlineAgain);
    };
  }, [onStatus]);

  return (
    <div className="verification-panel" aria-label="Contact verification">
      <p className="verification-intro">Verify your mobile number first. Then you can verify your email address.</p>
      {!online && <p className="verification-offline" role="status">You are offline. Reconnect to send or verify a code.</p>}
      {refreshError && <p className="verification-error" role="alert">{refreshError}</p>}
      {state?.deliveryMode === 'MOCK' && (
        <p className="verification-mock" role="status">
          Local development mode: no real SMS or email is sent. Test codes are available only in this local session.
        </p>
      )}
      <div className="verification-list">
        <ChannelVerification
          channel="mobile"
          state={state?.mobile}
          deliveryMode={state?.deliveryMode}
          online={online}
          onStatus={onStatus}
        />
        {mobileVerified ? (
          <ChannelVerification
            channel="email"
            state={state?.email}
            deliveryMode={state?.deliveryMode}
            online={online}
            onStatus={onStatus}
          />
        ) : (
          <section className="verification-channel is-locked" aria-label="Email verification locked">
            <div className="verification-channel-header">
              <h3>Email verification</h3>
              <span className="verification-status">Locked</span>
            </div>
            <p className="verification-hint">Verify your mobile number first to unlock email verification.</p>
          </section>
        )}
      </div>
      <p className="verification-footnote">Both contacts must be verified before you can continue.</p>
    </div>
  );
}
