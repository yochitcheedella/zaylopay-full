import { DigitalMembershipCard, AccountOverview } from './MembershipComponents';
import { AuthSuccess } from '../../Components/Auth/AuthComponents';
import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  CreditCard,
  Home,
  Info,
  LogOut,
  Receipt,
  Sparkles,
} from 'lucide-react';
import API from '../../Services/api';
import AuthLayout from '../../Components/Auth/AuthLayout';
import { AuthButton, AuthError, LoadingState } from '../../Components/Auth/AuthComponents';
import { resolvePostAuthRoute } from '../../utils/authRedirect';
import './MemberWelcomePage.css';

function formatActivationDate(isoString) {
  if (!isoString) return null;
  try {
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Asia/Kolkata',
    }).format(new Date(isoString));
  } catch {
    return null;
  }
}

export default function MemberWelcomePage() {
  const navigate = useNavigate();
  const location = useLocation();

  const [animateCheck] = useState(() => Boolean(location.state?.justAuthenticated));
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [membership, setMembership] = useState(null);
  const [showDetails, setShowDetails] = useState(false);
  const [showReceipt, setShowReceipt] = useState(false);
  const [copiedId, setCopiedId] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  // Clear one-shot login entrance animation state so browser refresh stays calm
  useEffect(() => {
    if (location.state?.justAuthenticated) {
      navigate(location.pathname, { replace: true, state: {} });
    }
  }, [location.pathname, location.state, navigate]);

  const fetchAuthenticatedMembership = async () => {
    setLoading(true);
    setLoadError('');
    try {
      const sessionRes = await API.get('/auth/session');
      const sessionData = sessionRes?.data;

      if (!sessionData?.authenticated || sessionData?.sessionState !== 'ACTIVE') {
        navigate('/login', { replace: true });
        return;
      }

      if (!sessionData.canAccessDashboard) {
        navigate(resolvePostAuthRoute(sessionData.eligibleStep), { replace: true });
        return;
      }

      const memberRes = await API.get('/applicants/me/membership');
      const memberData = memberRes?.data;
      if (!memberData?.affiliateId || memberData.membershipStatus !== 'ACTIVE') {
        navigate('/register?step=4', { replace: true });
        return;
      }
      setMembership(memberData);
    } catch (err) {
      const status = err.response?.status;
      if (status === 401) {
        navigate('/login', { replace: true });
        return;
      }
      if (status === 403) {
        navigate('/register?step=4', { replace: true });
        return;
      }
      setLoadError(
        err.response?.data?.detail ||
          'Unable to verify your membership details right now. Please check your connection and retry.'
      );
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    async function loadOnMount() {
      try {
        const sessionRes = await API.get('/auth/session');
        if (!active) return;
        const sessionData = sessionRes?.data;

        if (!sessionData?.authenticated || sessionData?.sessionState !== 'ACTIVE') {
          navigate('/login', { replace: true });
          return;
        }
        if (!sessionData.canAccessDashboard) {
          navigate(resolvePostAuthRoute(sessionData.eligibleStep), { replace: true });
          return;
        }

        const memberRes = await API.get('/applicants/me/membership');
        if (!active) return;
        if (!memberRes?.data?.affiliateId || memberRes.data.membershipStatus !== 'ACTIVE') {
          navigate('/register?step=4', { replace: true });
          return;
        }
        setMembership(memberRes.data);
      } catch (err) {
        if (!active) return;
        const status = err.response?.status;
        if (status === 401) {
          navigate('/login', { replace: true });
          return;
        }
        if (status === 403) {
          navigate('/register?step=4', { replace: true });
          return;
        }
        setLoadError(
          err.response?.data?.detail ||
            'Unable to verify your membership details right now. Please check your connection and retry.'
        );
      } finally {
        if (active) setLoading(false);
      }
    }

    loadOnMount();
    return () => {
      active = false;
    };
  }, [navigate]);

  const handleCopyAffiliateId = async () => {
    if (!membership?.affiliateId) return;
    try {
      await navigator.clipboard.writeText(membership.affiliateId);
      setCopiedId(true);
      setTimeout(() => setCopiedId(false), 2000);
    } catch {
      // Clipboard blocked in restricted browser contexts
    }
  };

  const handleCopyReferralLink = async () => {
    const link =
      membership?.referralUrl ||
      (membership?.referralCode
        ? `${window.location.origin}/register?ref=${membership.referralCode}`
        : '');
    if (!link) return;
    try {
      await navigator.clipboard.writeText(link);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    } catch {
      // Clipboard blocked in restricted browser contexts
    }
  };

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await API.post('/auth/logout', {});
      sessionStorage.removeItem('zaylopeToken');
      sessionStorage.removeItem('zaylopeUserName');
      sessionStorage.removeItem('zaylopeEmail');
      setMembership(null);
      navigate('/login', { replace: true });
    } catch {
      setLoadError('Unable to sign out. Please check your connection and try again.');
    } finally {
      setLoggingOut(false);
    }
  };

  const displayName = membership?.applicantName?.trim() || 'Member';
  const formattedDate = formatActivationDate(membership?.activatedAt);
  const amountRupees = membership?.amountPaise ? Math.round(membership.amountPaise / 100) : 599;
  const currency = membership?.currency || 'INR';
  const referralUrl =
    membership?.referralUrl ||
    (membership?.referralCode
      ? `${window.location.origin}/register?ref=${membership.referralCode}`
      : '');
  const hasReceiptData = Boolean(membership?.paymentId || membership?.orderId);

  return (
    <AuthLayout cardSize="status" showBrandLogo={true}>
      {loading ? (
        <LoadingState message="Verifying your ZayloPe membership…" />
      ) : loadError ? (
        <div className="zp-welcome-error-box">
          <AuthError error={loadError} title="Unable to load membership" />
          <div className="zp-welcome-error-actions">
            <AuthButton type="button" variant="primary" onClick={fetchAuthenticatedMembership}>
              Retry
            </AuthButton>
            <AuthButton type="button" variant="secondary" loading={loggingOut} onClick={handleLogout}>
              Log out
            </AuthButton>
          </div>
        </div>
      ) : membership ? (
        <div className="zp-welcome-view" data-testid="member-welcome-page">
          <AuthSuccess animate={animateCheck} />

          <div className="zp-welcome-pill">
            <Sparkles size={14} strokeWidth={2.2} aria-hidden="true" />
            <span>Lifetime Membership Active</span>
          </div>

          <div className="zp-welcome-header">
            <h1 className="zp-welcome-title">Welcome back, {displayName}!</h1>
            <p className="zp-welcome-subtitle">
              You&apos;ve successfully logged in to your ZayloPe account.
            </p>
          </div>

          {/* Premium Forest-Green Membership Card */}
          <DigitalMembershipCard membership={membership} displayName={displayName} formattedDate={formattedDate} copiedId={copiedId} handleCopyAffiliateId={handleCopyAffiliateId} />

          {/* Honest Future Dashboard Notice (No Fake Metrics) */}
          <div className="zp-welcome-dashboard-note" role="note">
            <Info size={18} strokeWidth={2} className="zp-welcome-note-icon" aria-hidden="true" />
            <div>
              <strong>Affiliate dashboard launching soon</strong>
              <p>
                Partner earnings, campaign links, and payout tracking will appear here once the full
                affiliate portal goes live. Your permanent Affiliate ID is safely active.
              </p>
            </div>
          </div>

          {/* Quick Secondary Panels */}
          <div className="zp-welcome-toggle-row">
            <button
              type="button"
              className="zp-welcome-toggle-btn"
              aria-expanded={showDetails}
              onClick={() => setShowDetails((prev) => !prev)}
            >
              <CreditCard size={16} strokeWidth={2} aria-hidden="true" />
              <span>{showDetails ? 'Hide membership details' : 'Membership details'}</span>
            </button>

            {hasReceiptData && (
              <button
                type="button"
                className="zp-welcome-toggle-btn"
                aria-expanded={showReceipt}
                onClick={() => setShowReceipt((prev) => !prev)}
              >
                <Receipt size={16} strokeWidth={2} aria-hidden="true" />
                <span>{showReceipt ? 'Hide payment receipt' : 'Payment receipt'}</span>
              </button>
            )}
          </div>

          {showDetails && (
            <AccountOverview membership={membership} referralUrl={referralUrl} copiedLink={copiedLink} handleCopyReferralLink={handleCopyReferralLink} />
          )}

          {showReceipt && hasReceiptData && (
            <div className="zp-welcome-drawer" data-testid="welcome-payment-receipt">
              <div className="zp-welcome-drawer-row">
                <span>Receipt Status</span>
                <strong>Verified &amp; Captured</strong>
              </div>
              {membership.orderId && (
                <div className="zp-welcome-drawer-row">
                  <span>Order Reference</span>
                  <code className="zp-welcome-drawer-code">{membership.orderId}</code>
                </div>
              )}
              {membership.paymentId && (
                <div className="zp-welcome-drawer-row">
                  <span>Payment ID</span>
                  <code className="zp-welcome-drawer-code">{membership.paymentId}</code>
                </div>
              )}
              <div className="zp-welcome-drawer-row">
                <span>Amount Paid (incl. 18% GST)</span>
                <strong>
                  ₹{amountRupees} {currency}
                </strong>
              </div>
            </div>
          )}

          {/* Footer Navigation Actions */}
          <button type="button" className="zp-welcome-home-btn" aria-controls="account-overview" aria-expanded={showDetails} onClick={() => setShowDetails(true)}>
            Continue to my account
          </button>
          <div className="zp-welcome-actions">
            <Link to="/" className="zp-welcome-home-btn">
              <Home size={16} strokeWidth={2.1} aria-hidden="true" />
              <span>Return to homepage</span>
            </Link>
            <AuthButton
              type="button"
              variant="secondary"
              loading={loggingOut}
              loadingLabel="Logging out…"
              onClick={handleLogout}
              className="zp-welcome-logout-btn"
            >
              <LogOut size={16} strokeWidth={2.1} aria-hidden="true" />
              <span>Log out</span>
            </AuthButton>
          </div>
        </div>
      ) : null}
    </AuthLayout>
  );
}
