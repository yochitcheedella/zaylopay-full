import React from 'react';
import { UserX } from 'lucide-react';
import { AuthButton } from './AuthComponents';
import './AccountNotFound.css';

/**
 * Reusable Account-Not-Found panel rendered ONLY after secure ownership
 * verification (e.g. verified Google ID token) or when safe to disclose.
 */
export default function AccountNotFound({
  identifier = '',
  identifierType = 'email', // 'email' | 'mobile'
  pictureUrl = null,
  onCreateAccount,
  onTryAnother,
  onBackToLogin,
  disabled = false,
}) {
  const isMobile = identifierType === 'mobile';
  const subjectText = isMobile
    ? 'This mobile number is not registered with ZayloPe.'
    : 'This email address is not registered with ZayloPe.';
  const helpText = isMobile
    ? 'Please check your mobile number or create a new account.'
    : 'Please check your email address or create a new account.';

  return (
    <section
      className="zp-not-found-panel"
      aria-live="polite"
      data-testid="account-not-found"
    >
      <div className="zp-not-found-icon" aria-hidden="true">
        <UserX size={28} strokeWidth={2} />
      </div>

      <h2 className="zp-not-found-title">Account not found</h2>
      <p className="zp-not-found-desc">{subjectText}</p>
      <p className="zp-not-found-help">{helpText}</p>

      {identifier && (
        <div className="zp-not-found-identity" data-testid="not-found-identifier">
          {pictureUrl && (
            <img
              src={pictureUrl}
              alt=""
              className="zp-not-found-avatar"
              referrerPolicy="no-referrer"
            />
          )}
          <span className="zp-not-found-value">{identifier}</span>
        </div>
      )}

      <div className="zp-not-found-actions">
        <AuthButton
          type="button"
          variant="primary"
          disabled={disabled}
          onClick={onCreateAccount}
        >
          Create an account
        </AuthButton>
        <AuthButton
          type="button"
          variant="secondary"
          disabled={disabled}
          onClick={onTryAnother}
        >
          Try another email or mobile
        </AuthButton>
        {onBackToLogin && (
          <button
            type="button"
            className="zp-not-found-back-btn"
            disabled={disabled}
            onClick={onBackToLogin}
          >
            Back to login
          </button>
        )}
      </div>
    </section>
  );
}
