import React from 'react';
import { Smartphone, Mail, Check } from 'lucide-react';

/**
 * Accessible two-option contact verification selector (Mobile SMS vs Email).
 * Shows masked destinations and exact verification status without inferring
 * verification across channels.
 */
export default function VerificationMethodSelector({
  selectedChannel = 'mobile',
  onSelect,
  mobileVerified = false,
  emailVerified = false,
  emailVerificationMethod = 'NONE',
  maskedMobile = '',
  maskedEmail = '',
  disabled = false,
}) {
  const emailBadgeText =
    emailVerificationMethod === 'GOOGLE' ? 'Email verified with Google' : 'Verified';

  return (
    <div className="zp-method-selector-wrap">
      <span className="zp-method-selector-label" id="otp-method-label">
        Verification method
      </span>
      <div
        className="zp-method-selector"
        role="radiogroup"
        aria-labelledby="otp-method-label"
      >
        <button
          type="button"
          role="radio"
          aria-checked={selectedChannel === 'mobile'}
          disabled={disabled}
          className={`zp-method-option ${selectedChannel === 'mobile' ? 'is-selected' : ''}`}
          onClick={() => onSelect('mobile')}
        >
          <div className="zp-method-option-head">
            <span className="zp-method-icon" aria-hidden="true">
              <Smartphone size={18} strokeWidth={2} />
            </span>
            <span className="zp-method-option-title">Mobile number</span>
            {mobileVerified && (
              <span className="zp-method-verified-pill">
                <Check size={12} strokeWidth={2.5} aria-hidden="true" />
                <span>Verified</span>
              </span>
            )}
          </div>
          <span className="zp-method-option-sub">Receive an SMS code</span>
          {maskedMobile && (
            <span className="zp-method-option-dest">{maskedMobile}</span>
          )}
        </button>

        <button
          type="button"
          role="radio"
          aria-checked={selectedChannel === 'email'}
          disabled={disabled}
          className={`zp-method-option ${selectedChannel === 'email' ? 'is-selected' : ''}`}
          onClick={() => onSelect('email')}
        >
          <div className="zp-method-option-head">
            <span className="zp-method-icon" aria-hidden="true">
              <Mail size={18} strokeWidth={2} />
            </span>
            <span className="zp-method-option-title">Email address</span>
            {emailVerified && (
              <span className="zp-method-verified-pill">
                <Check size={12} strokeWidth={2.5} aria-hidden="true" />
                <span>{emailBadgeText}</span>
              </span>
            )}
          </div>
          <span className="zp-method-option-sub">Receive an email code</span>
          {maskedEmail && (
            <span className="zp-method-option-dest">{maskedEmail}</span>
          )}
        </button>
      </div>
    </div>
  );
}
