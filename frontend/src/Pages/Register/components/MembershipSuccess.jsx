import React, { useState } from 'react';
import { Check, Copy, Download, ShieldCheck, Sparkles, Receipt } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { LoadingButton, SecondaryButton } from './FormControls';
import { downloadReceiptPdf } from '../../../utils/receiptPdf';
import './MembershipSuccess.css';

function formatActivationDate(isoString) {
  if (!isoString) return '—';
  try {
    return new Date(isoString).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  } catch {
    return '—';
  }
}

export default function MembershipSuccess({
  membership = {},
  onOpenOverview,
}) {
  const navigate = useNavigate();
  const [copiedId, setCopiedId] = useState(false);
  const [copiedReferral, setCopiedReferral] = useState(false);
  const [showReceipt, setShowReceipt] = useState(false);
  const [isDownloadingReceipt, setIsDownloadingReceipt] = useState(false);
  const [downloadError, setDownloadError] = useState('');

  const memberName = (membership?.applicantName || '').trim();
  const affiliateId = membership?.affiliateId || '';
  const referralUrl = membership?.referralUrl || '';
  const activatedAt = formatActivationDate(membership?.activatedAt);
  const amountPaise = Number(membership?.amountPaise || 59900);
  const currency = membership?.currency || 'INR';
  const formattedFee = `₹${Math.round(amountPaise / 100)} ${currency}`;

  const copyText = (value, onCopied) => {
    if (!value) return;
    if (navigator?.clipboard?.writeText) {
      navigator.clipboard.writeText(value).then(() => {
        onCopied(true);
        setTimeout(() => onCopied(false), 2200);
      });
    }
  };

  const hasReceipt = Boolean(membership?.paymentId || membership?.orderId);

  const handleDownloadReceipt = async () => {
    if (!hasReceipt || isDownloadingReceipt) return;
    setIsDownloadingReceipt(true);
    setDownloadError('');
    try {
      await downloadReceiptPdf(membership);
    } catch (err) {
      setDownloadError(err?.message || 'Unable to download receipt PDF. Please try again.');
    } finally {
      setIsDownloadingReceipt(false);
    }
  };

  const handleOpenOverview = () => {
    if (onOpenOverview) {
      onOpenOverview();
    } else {
      navigate('/dashboard');
    }
  };

  return (
    <div className="zp-ms-container" data-testid="membership-success">
      <div className="zp-ms-check-wrap" aria-hidden="true">
        <div className="zp-ms-check-circle">
          <Check size={28} strokeWidth={2.8} className="zp-ms-check-icon" />
        </div>
      </div>

      <div className="zp-ms-header">
        <span className="zp-ms-pill">
          <Sparkles size={12} aria-hidden="true" />
          Lifetime Membership Active
        </span>
        <h1 className="zp-ms-title">
          {memberName ? `Congratulations, ${memberName}!` : 'Congratulations!'}
        </h1>
        <p className="zp-ms-subtitle">
          Your ZayloPe lifetime membership is now active. Your permanent Affiliate ID is ready.
        </p>
      </div>

      {/* Forest-Green Digital Membership Card */}
      <section className="zp-digital-card" aria-label="Digital ZayloPe Lifetime Membership Card">
        <div className="zp-digital-card-glow" aria-hidden="true" />
        <div className="zp-digital-card-top">
          <div className="zp-dc-brand">
            <span className="zp-dc-logo-box" aria-hidden="true">Z</span>
            <span className="zp-dc-brand-text">
              Zaylo<span>Pe</span>
            </span>
          </div>
          <span className="zp-dc-tier-pill">Lifetime Member</span>
        </div>

        <div className="zp-dc-id-block">
          <span className="zp-dc-id-label">Permanent Affiliate ID</span>
          <div className="zp-dc-id-row">
            <code className="zp-dc-id-value" data-testid="permanent-affiliate-id">
              {affiliateId || '—'}
            </code>
            {affiliateId && (
              <button
                type="button"
                className="zp-dc-copy-btn"
                onClick={() => copyText(affiliateId, setCopiedId)}
                aria-label="Copy Affiliate ID"
              >
                {copiedId ? <Check size={14} aria-hidden="true" /> : <Copy size={14} aria-hidden="true" />}
                <span>{copiedId ? 'Copied!' : 'Copy ID'}</span>
              </button>
            )}
          </div>
          <span className="sr-only" aria-live="polite">
            {copiedId ? `Affiliate ID ${affiliateId} copied to clipboard.` : ''}
          </span>
        </div>

        <div className="zp-dc-meta-row">
          <div className="zp-dc-meta-item">
            <span className="zp-dc-meta-label">Member Name</span>
            <span className="zp-dc-meta-value" data-testid="membership-member-name">
              {memberName || 'ZayloPe Partner'}
            </span>
          </div>
          <div className="zp-dc-meta-item">
            <span className="zp-dc-meta-label">Status</span>
            <span className="zp-dc-meta-value zp-dc-status-active">
              <ShieldCheck size={14} aria-hidden="true" />
              Active
            </span>
          </div>
          <div className="zp-dc-meta-item">
            <span className="zp-dc-meta-label">Activated On</span>
            <span className="zp-dc-meta-value">{activatedAt}</span>
          </div>
        </div>
      </section>

      {/* Verification & Payment Summary Row */}
      <div className="zp-ms-summary-grid">
        <div className="zp-ms-summary-cell">
          <span className="zp-ms-summary-label">Membership Fee</span>
          <span className="zp-ms-summary-value zp-ms-paid">{formattedFee} • Captured</span>
        </div>
        <div className="zp-ms-summary-cell">
          <span className="zp-ms-summary-label">Account Status</span>
          <span className="zp-ms-summary-value">{membership?.affiliateStatus || 'ACTIVE'}</span>
        </div>
        <div className="zp-ms-summary-cell">
          <span className="zp-ms-summary-label">Plan Type</span>
          <span className="zp-ms-summary-value">{membership?.membershipType || 'LIFETIME'}</span>
        </div>
      </div>

      {referralUrl && (
        <div className="zp-ms-referral-bar">
          <div className="zp-ms-referral-text">
            <span className="zp-ms-summary-label">Your Referral Link</span>
            <span className="zp-ms-referral-url">{referralUrl}</span>
          </div>
          <button
            type="button"
            className="zp-ms-ref-copy-btn"
            onClick={() => copyText(referralUrl, setCopiedReferral)}
            aria-label="Copy referral link"
          >
            {copiedReferral ? 'Copied!' : 'Copy Link'}
          </button>
        </div>
      )}

      {showReceipt && (membership?.paymentId || membership?.orderId) && (
        <div className="zp-ms-receipt-box" role="region" aria-label="Payment receipt details">
          <div className="zp-ms-receipt-row">
            <span>Receipt Status</span>
            <strong>Verified &amp; Captured</strong>
          </div>
          {membership?.orderId && (
            <div className="zp-ms-receipt-row">
              <span>Order Reference</span>
              <code>{membership.orderId}</code>
            </div>
          )}
          {membership?.paymentId && (
            <div className="zp-ms-receipt-row">
              <span>Payment ID</span>
              <code>{membership.paymentId}</code>
            </div>
          )}
          <div className="zp-ms-receipt-row">
            <span>Amount Paid (incl. 18% GST)</span>
            <strong>{formattedFee}</strong>
          </div>
        </div>
      )}

      <div className="zp-ms-actions">
        <LoadingButton type="button" onClick={handleOpenOverview} className="zp-ms-primary-btn">
          Open Membership Overview
        </LoadingButton>
        {hasReceipt && (
          <div className="zp-ms-receipt-actions">
            <SecondaryButton
              type="button"
              icon={<Receipt size={16} aria-hidden="true" />}
              onClick={() => setShowReceipt((v) => !v)}
            >
              {showReceipt ? 'Hide receipt' : 'View payment receipt'}
            </SecondaryButton>
            <SecondaryButton
              type="button"
              icon={<Download size={16} aria-hidden="true" />}
              onClick={handleDownloadReceipt}
              disabled={isDownloadingReceipt}
              aria-busy={isDownloadingReceipt}
            >
              {isDownloadingReceipt ? 'Preparing PDF...' : 'Download Receipt'}
            </SecondaryButton>
          </div>
        )}
        {downloadError && (
          <div className="zp-ms-receipt-error" role="alert">
            <span>{downloadError}</span>
            <button
              type="button"
              className="zp-ms-retry-btn"
              onClick={handleDownloadReceipt}
              disabled={isDownloadingReceipt}
            >
              Retry
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
