import React from 'react';
import './VerificationStatus.css';

export function VerifiedBadge({ label = 'Verified' }) {
  return (
    <span className="zp-verified-badge">
      <svg className="zp-verified-icon" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
        <path fillRule="evenodd" d="M12.416 3.376a.75.75 0 0 1 .208 1.04l-5 7.5a.75.75 0 0 1-1.154.114l-3-3a.75.75 0 0 1 1.06-1.06l2.353 2.353 4.493-6.74a.75.75 0 0 1 1.04-.207z" clipRule="evenodd"/>
      </svg>
      <span>{label}</span>
    </span>
  );
}

export function ChannelVerifiedCard({ title, destination, badgeLabel = 'Verified' }) {
  return (
    <section className="zp-channel-card is-verified" aria-label={`${title} verified`}>
      <div className="zp-channel-header">
        <h3 className="zp-channel-title">{title}</h3>
        <VerifiedBadge label={badgeLabel} />
      </div>
      <p className="zp-channel-destination">{destination}</p>
    </section>
  );
}

export function ChannelLockedCard({ title, reason }) {
  return (
    <section className="zp-channel-card is-locked" aria-label={`${title} locked`}>
      <div className="zp-channel-header">
        <h3 className="zp-channel-title">{title}</h3>
        <span className="zp-locked-badge">
          <svg className="zp-locked-icon" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path fillRule="evenodd" d="M4 5a3 3 0 0 1 6 0v1h1a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h1V5zm2 1h2V5a1 1 0 0 0-2 0v1z" clipRule="evenodd"/>
          </svg>
          <span>Locked</span>
        </span>
      </div>
      <p className="zp-channel-hint">{reason}</p>
    </section>
  );
}
