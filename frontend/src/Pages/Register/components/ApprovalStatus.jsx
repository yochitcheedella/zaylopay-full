import React, { useEffect, useState, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import API from '../../../Services/api';
import { LoadingButton, SecondaryButton } from './FormControls';
import MembershipSuccess from './MembershipSuccess';
import { resolvePostAuthRoute } from '../../../utils/authRedirect';
import './ApprovalStatus.css';

/**
 * Step 4 Membership Activation & Status Screen for ZayloPe.
 * Supports backend-driven lifecycle states without requiring admin approval:
 * 1. ACTIVE: Permanent Affiliate ID + Lifetime Membership active -> renders MembershipSuccess.
 * 2. PENDING_ACTIVATION: Captured payment confirmed, activation recovery available.
 * 3. PAYMENT_PENDING: Payment incomplete -> resumes checkout cleanly.
 * 4. RESTRICTED: Account restricted/suspended/rejected -> shows restriction notice.
 */
export default function ApprovalStatus({
  applicantName = '',
  initialStatus = null,
}) {
  const navigate = useNavigate();
  const [statusData, setStatusData] = useState(initialStatus);
  const [membershipDetails, setMembershipDetails] = useState(null);
  const [loading, setLoading] = useState(!initialStatus);
  const [refreshing, setRefreshing] = useState(false);
  const [activating, setActivating] = useState(false);
  const [error, setError] = useState('');
  const [sessionExpired, setSessionExpired] = useState(false);

  const loadMembershipState = useCallback(async (isManualRefresh = false) => {
    if (isManualRefresh) {
      setRefreshing(true);
      setError('');
      setSessionExpired(false);
    }

    try {
      const statusRes = await API.get('/applicants/me/membership/status');
      const status = statusRes.data;
      setStatusData(status);

      if (status?.paymentStatus !== 'PAID') {
        try {
          const payRes = await API.get('/applicants/me/payments/status');
          if (payRes.data?.paid || payRes.data?.paymentStatus === 'PAID') {
            const refreshed = await API.get('/applicants/me/membership/status');
            if (refreshed.data) {
              setStatusData(refreshed.data);
            }
          }
        } catch {
          // Keep current membership status state
        }
      }

      if (status?.membershipStatus === 'ACTIVE' || status?.affiliateId) {
        try {
          const membershipRes = await API.get('/applicants/me/membership');
          if (membershipRes.data) {
            setMembershipDetails(membershipRes.data);
          }
        } catch {
          // Status payload still contains authoritative summary fields
        }
      }
    } catch (err) {
      if (err.response?.status === 401) {
        setSessionExpired(true);
      } else {
        setError('Unable to fetch the latest membership status. Please try again.');
      }
    } finally {
      setLoading(false);
      if (isManualRefresh) {
        setRefreshing(false);
      }
    }
  }, []);

  useEffect(() => {
    let ignore = false;
    API.get('/applicants/me/membership/status')
      .then(async (statusRes) => {
        if (ignore) return;
        const status = statusRes.data;
        setStatusData(status);

        if (status?.membershipStatus === 'ACTIVE' || status?.affiliateId) {
          try {
            const membershipRes = await API.get('/applicants/me/membership');
            if (!ignore && membershipRes.data) {
              setMembershipDetails(membershipRes.data);
            }
          } catch {
            // Status payload still contains authoritative summary fields
          }
        }
      })
      .catch((err) => {
        if (ignore) return;
        if (err.response?.status === 401) {
          setSessionExpired(true);
        } else {
          setError('Unable to fetch the latest membership status. Please try again.');
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, []);

  const handleRecoverActivation = async () => {
    if (activating) return;
    setActivating(true);
    setError('');
    try {
      const res = await API.post('/applicants/me/membership/activate', {});
      if (res.data) {
        setMembershipDetails(res.data);
      }
      await loadMembershipState(false);
    } catch (err) {
      if (err.response?.status === 401) {
        setSessionExpired(true);
      } else {
        setError(
          err.response?.data?.detail ||
            'Activation is still completing. Your payment is safe — please retry in a moment.'
        );
      }
    } finally {
      setActivating(false);
    }
  };

  if (sessionExpired) {
    return (
      <div className="zp-status-view" role="alert">
        <div className="zp-status-badge zp-badge-session">
          <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83" />
          </svg>
        </div>
        <div className="zp-status-header">
          <h1 className="zp-status-title">Log in required</h1>
          <p className="zp-status-subtitle">
            Your verification timed out for security. Please log in again to view your lifetime membership.
          </p>
        </div>
        <div className="zp-status-actions">
          <Link to="/login" className="zp-login-link-btn">
            Log in to ZayloPe
          </Link>
        </div>
      </div>
    );
  }

  if (loading && !statusData) {
    return (
      <div className="zp-status-view zp-status-loading" aria-busy="true">
        <div className="zp-skeleton-badge" />
        <div className="zp-skeleton-line zp-line-title" />
        <div className="zp-skeleton-line zp-line-subtitle" />
        <div className="zp-skeleton-card" />
      </div>
    );
  }

  const paymentStatus = (statusData?.paymentStatus || 'PENDING').toUpperCase();
  const approvalStatus = (statusData?.approvalStatus || 'PENDING').toUpperCase();
  const membershipStatus = (
    membershipDetails?.membershipStatus ||
    statusData?.membershipStatus ||
    ''
  ).toUpperCase();
  const affiliateStatus = (
    membershipDetails?.affiliateStatus ||
    statusData?.affiliateStatus ||
    'ACTIVE'
  ).toUpperCase();

  const affiliateId = membershipDetails?.affiliateId || statusData?.affiliateId;
  const isRestricted =
    approvalStatus === 'REJECTED' ||
    membershipStatus === 'REVOKED' ||
    affiliateStatus === 'SUSPENDED' ||
    affiliateStatus === 'CLOSED';

  const isActive =
    !isRestricted &&
    Boolean(affiliateId) &&
    (membershipStatus === 'ACTIVE' || paymentStatus === 'PAID');

  const isActivationRecovery =
    !isRestricted && !isActive && (paymentStatus === 'PAID' || paymentStatus === 'CAPTURED');

  if (isActive) {
    const resolvedMembership = {
      applicantName: membershipDetails?.applicantName || statusData?.applicantName || applicantName,
      affiliateId,
      referralCode: membershipDetails?.referralCode || statusData?.referralCode,
      referralUrl: membershipDetails?.referralUrl || statusData?.referralUrl,
      activatedAt: membershipDetails?.activatedAt || statusData?.approvedAt,
      amountPaise: membershipDetails?.amountPaise || statusData?.amountPaise || 59900,
      currency: membershipDetails?.currency || statusData?.currency || 'INR',
      membershipType: membershipDetails?.membershipType || statusData?.membershipType || 'LIFETIME',
      affiliateStatus: membershipDetails?.affiliateStatus || statusData?.affiliateStatus || 'ACTIVE',
      orderId: membershipDetails?.orderId,
      paymentId: membershipDetails?.paymentId || statusData?.paymentId,
    };

    return (
      <MembershipSuccess
        membership={resolvedMembership}
        onOpenOverview={() => navigate(resolvePostAuthRoute('DASHBOARD'))}
      />
    );
  }

  const referenceId =
    statusData?.registrationId ||
    (statusData?.applicantId ? `APP-${statusData.applicantId}` : '—');

  return (
    <div className="zp-status-view">
      {error && (
        <div className="zp-status-error-banner" role="alert">
          <span>{error}</span>
          <button
            type="button"
            className="zp-error-retry-btn"
            onClick={() => loadMembershipState(true)}
            disabled={refreshing}
          >
            Retry
          </button>
        </div>
      )}

      {isRestricted ? (
        <>
          <div className="zp-status-badge zp-badge-rejected">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="15" y1="9" x2="9" y2="15" />
              <line x1="9" y1="9" x2="15" y2="15" />
            </svg>
          </div>
          <div className="zp-status-header">
            <span className="zp-status-pill zp-pill-rejected">Account Restricted</span>
            <h1 className="zp-status-title">Account Restricted</h1>
            <p className="zp-status-subtitle">
              Your account is currently restricted from accessing affiliate membership benefits.
            </p>
          </div>
          {statusData?.rejectionReason && (
            <div className="zp-feedback-box zp-feedback-rejected">
              <p className="zp-feedback-text">{statusData.rejectionReason}</p>
            </div>
          )}
          <p className="zp-support-text">
            Contact{' '}
            <a href="mailto:support@zaylope.com" className="zp-support-anchor">
              support@zaylope.com
            </a>{' '}
            for account assistance.
          </p>
        </>
      ) : isActivationRecovery ? (
        <>
          <div className="zp-status-badge zp-badge-pending">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
          </div>

          <div className="zp-status-header">
            <span className="zp-status-pill zp-pill-pending">Payment Confirmed</span>
            <h1 className="zp-status-title">Completing membership activation</h1>
            <p className="zp-status-subtitle">
              Your payment is confirmed. We&apos;re completing your membership activation.
            </p>
          </div>

          <div className="zp-status-meta-grid">
            <div className="zp-meta-cell">
              <span className="zp-meta-label">Application Ref</span>
              <span className="zp-meta-value zp-meta-ref">{referenceId}</span>
            </div>
            <div className="zp-meta-cell">
              <span className="zp-meta-label">Membership Fee</span>
              <span className="zp-meta-value zp-meta-paid">₹599 • Captured</span>
            </div>
            <div className="zp-meta-cell">
              <span className="zp-meta-label">Activation</span>
              <span className="zp-meta-value">Finalizing Affiliate ID</span>
            </div>
          </div>

          <div className="zp-status-actions">
            <LoadingButton
              type="button"
              loading={activating}
              loadingLabel="Completing activation…"
              onClick={handleRecoverActivation}
              className="zp-primary-cta-btn"
            >
              Complete Activation
            </LoadingButton>
            <SecondaryButton
              type="button"
              className="zp-refresh-btn"
              disabled={refreshing || activating}
              onClick={() => loadMembershipState(true)}
            >
              {refreshing ? 'Checking status…' : 'Refresh Status'}
            </SecondaryButton>
          </div>
        </>
      ) : (
        <>
          <div className="zp-status-badge zp-badge-warning">
            <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="2" y="5" width="20" height="14" rx="2" />
              <line x1="2" y1="10" x2="22" y2="10" />
            </svg>
          </div>

          <div className="zp-status-header">
            <span className="zp-status-pill zp-pill-warning">Payment Pending</span>
            <h1 className="zp-status-title">Complete your ₹599 payment</h1>
            <p className="zp-status-subtitle">
              Your application is saved. Complete your verified ₹599 payment to activate your
              lifetime membership and receive your permanent Affiliate ID immediately.
            </p>
          </div>

          <div className="zp-status-actions">
            <LoadingButton
              type="button"
              className="zp-primary-cta-btn"
              onClick={() => navigate('/register?step=3')}
            >
              Complete ₹599 Payment
            </LoadingButton>
            <SecondaryButton
              type="button"
              className="zp-refresh-btn"
              disabled={refreshing}
              onClick={() => loadMembershipState(true)}
            >
              {refreshing ? 'Checking status…' : 'Check Payment Status'}
            </SecondaryButton>
          </div>
        </>
      )}
    </div>
  );
}
