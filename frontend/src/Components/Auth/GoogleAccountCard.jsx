import React, { useState } from 'react';
import './GoogleAccountCard.css';

function computeInitials(fullName, email) {
  const source = (fullName || '').trim() || (email || '').split('@')[0] || 'Z';
  const parts = source.split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }
  return source.slice(0, 2).toUpperCase();
}

export default function GoogleAccountCard({
  fullName,
  email,
  pictureUrl,
  authoritativeEmail = false,
  badgeMode = 'completion', // 'completion' | 'linking'
  onUseDifferentAccount,
  disabled = false,
}) {
  const [imageFailed, setImageFailed] = useState(false);
  const showImage = Boolean(pictureUrl) && !imageFailed;
  const initials = computeInitials(fullName, email);

  return (
    <section className="zp-google-account-card" aria-label="Connected Google account">
      <div className="zp-google-account-row">
        <div className="zp-google-avatar" aria-hidden="true">
          {showImage ? (
            <img
              src={pictureUrl}
              alt=""
              referrerPolicy="no-referrer"
              onError={() => setImageFailed(true)}
            />
          ) : (
            <span>{initials}</span>
          )}
        </div>

        <div className="zp-google-account-meta">
          {fullName && <p className="zp-google-account-name">{fullName}</p>}
          {email && <p className="zp-google-account-email">{email}</p>}

          {badgeMode === 'completion' && (
            authoritativeEmail ? (
              <span className="zp-google-verified-pill">
                <svg viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
                  <path
                    fillRule="evenodd"
                    d="M12.416 3.376a.75.75 0 0 1 .208 1.04l-5 7.5a.75.75 0 0 1-1.154.114l-3-3a.75.75 0 0 1 1.06-1.06l2.353 2.353 4.493-6.74a.75.75 0 0 1 1.04-.207z"
                    clipRule="evenodd"
                  />
                </svg>
                <span>Email verified with Google</span>
              </span>
            ) : (
              <span className="zp-google-pending-pill">
                Additional contact verification needed next
              </span>
            )
          )}
        </div>
      </div>

      {onUseDifferentAccount && (
        <div className="zp-google-account-footer">
          <button
            type="button"
            className="zp-google-switch-btn"
            onClick={onUseDifferentAccount}
            disabled={disabled}
          >
            Use a different Google account
          </button>
        </div>
      )}
    </section>
  );
}
