import { Check, Copy, ShieldCheck } from 'lucide-react';

// Data-only views: the protected parent resolves the session and membership APIs.
export function DigitalMembershipCard({membership, displayName, formattedDate, copiedId, handleCopyAffiliateId}) {
  if (membership?.membershipStatus !== 'ACTIVE' || !membership?.affiliateId) return null;
  return (
          <section className="zp-welcome-card" aria-label="Official ZayloPe Membership Card">
            <div className="zp-welcome-card-top">
              <div className="zp-welcome-card-brand">
                <span className="zp-welcome-card-z" aria-hidden="true">Z</span>
                <span className="zp-welcome-card-brand-text">
                  Zaylo<span>Pe</span>
                </span>
              </div>
              <span className="zp-welcome-tier-pill">
                {membership.tier === 'LIFETIME' ? 'Lifetime Member' : membership.tier || 'Member'}
              </span>
            </div>

            <div className="zp-welcome-id-block">
              <span className="zp-welcome-id-label">Permanent Affiliate ID</span>
              <div className="zp-welcome-id-row">
                <code className="zp-welcome-id-code" data-testid="welcome-affiliate-id">
                  {membership.affiliateId}
                </code>
                <button
                  type="button"
                  className={`zp-welcome-copy-btn ${copiedId ? 'is-copied' : ''}`}
                  onClick={handleCopyAffiliateId}
                  aria-label="Copy Affiliate ID"
                >
                  {copiedId ? (
                    <>
                      <Check size={14} strokeWidth={2.5} aria-hidden="true" />
                      <span>Copied!</span>
                    </>
                  ) : (
                    <>
                      <Copy size={14} strokeWidth={2.2} aria-hidden="true" />
                      <span>Copy ID</span>
                    </>
                  )}
                </button>
              </div>
              <span className="zp-sr-only" aria-live="polite">
                {copiedId ? `Affiliate ID ${membership.affiliateId} copied to clipboard.` : ''}
              </span>
            </div>

            <div className="zp-welcome-card-footer">
              <div className="zp-welcome-meta">
                <span className="zp-welcome-meta-label">Member Name</span>
                <span className="zp-welcome-meta-val" data-testid="welcome-member-name">
                  {displayName}
                </span>
              </div>
              <div className="zp-welcome-meta">
                <span className="zp-welcome-meta-label">Status</span>
                <span className="zp-welcome-meta-status">
                  <ShieldCheck size={14} strokeWidth={2.4} aria-hidden="true" />
                  <span>{membership.membershipStatus === 'ACTIVE' ? 'Active' : membership.membershipStatus}</span>
                </span>
              </div>
              {formattedDate && (
                <div className="zp-welcome-meta">
                  <span className="zp-welcome-meta-label">Activated On</span>
                  <span className="zp-welcome-meta-val">{formattedDate}</span>
                </div>
              )}
            </div>
          </section>
  );
}

export function AccountOverview({membership, referralUrl, copiedLink, handleCopyReferralLink}) {
  if (!membership) return null;
  return (
            <div id="account-overview" className="zp-welcome-drawer" data-testid="welcome-membership-details">
              <div className="zp-welcome-drawer-row">
                <span>Membership Plan</span>
                <strong>ZayloPe Lifetime Membership</strong>
              </div>
              <div className="zp-welcome-drawer-row">
                <span>Account Status</span>
                <strong>{membership.affiliateStatus || 'ACTIVE'}</strong>
              </div>
              {referralUrl && (
                <div className="zp-welcome-referral-row">
                  <span>Referral Link</span>
                  <div className="zp-welcome-ref-box">
                    <code className="zp-welcome-ref-url">{referralUrl}</code>
                    <button
                      type="button"
                      className="zp-welcome-ref-copy"
                      onClick={handleCopyReferralLink}
                    >
                      {copiedLink ? 'Copied!' : 'Copy'}
                    </button>
                  </div>
                </div>
              )}
            </div>
  );
}
