import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { LoadingButton, SecondaryButton } from './FormControls';
import './ApplicationConfirmation.css';

export default function ApplicationConfirmation({
  applicantReference = 'APP-PENDING',
  applicationStatus = 'SUBMITTED',
  applicantName = '',
}) {
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(applicantReference);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div className="zp-confirmation-container">
      {/* Animated Success Checkmark */}
      <div className="zp-confirm-badge-wrap">
        <div className="zp-confirm-badge" aria-hidden="true">
          <svg viewBox="0 0 48 48" className="zp-confirm-svg">
            <circle className="zp-confirm-circle" cx="24" cy="24" r="22" fill="none" stroke="currentColor" strokeWidth="3" />
            <path className="zp-confirm-check" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round" d="M14 24l7 7 14-14" />
          </svg>
        </div>
      </div>

      <h2 className="zp-confirm-title">Application Submitted!</h2>
      <p className="zp-confirm-subtitle">
        {applicantName ? `Thank you, ${applicantName}. ` : 'Thank you. '}
        Your ZayloPe onboarding application has been securely received and submitted for compliance review.
      </p>

      {/* Reference Card */}
      <div className="zp-confirm-card">
        <div className="zp-confirm-row">
          <div className="zp-confirm-label-col">
            <span className="zp-confirm-label">Application Reference ID</span>
            <div className="zp-confirm-ref-val">
              <span className="zp-ref-number">{applicantReference}</span>
              <button
                type="button"
                className="zp-copy-btn"
                onClick={handleCopy}
                aria-label="Copy application reference number"
              >
                {copied ? (
                  <span className="zp-copied-text">Copied!</span>
                ) : (
                  <svg viewBox="0 0 20 20" fill="currentColor" width="16" height="16">
                    <path d="M8 3a1 1 0 011-1h2a1 1 0 110 2H9a1 1 0 01-1-1z" />
                    <path d="M6 3a2 2 0 00-2 2v11a2 2 0 002 2h8a2 2 0 002-2V5a2 2 0 00-2-2 3 3 0 01-3 3H9a3 3 0 01-3-3z" />
                  </svg>
                )}
              </button>
            </div>
          </div>
          <div className="zp-confirm-status-col">
            <span className="zp-confirm-label">Status</span>
            <span className="zp-confirm-status-pill">
              {applicationStatus === 'SUBMITTED' ? 'Under Review' : applicationStatus}
            </span>
          </div>
        </div>
      </div>

      {/* What Happens Next Timeline */}
      <div className="zp-timeline-section">
        <h3 className="zp-timeline-title">What happens next?</h3>
        <ol className="zp-timeline-list">
          <li className="zp-timeline-step is-active">
            <span className="zp-timeline-marker">1</span>
            <div className="zp-timeline-content">
              <strong>Identity & Document Verification</strong>
              <p>Our verification team reviews your uploaded documents within 24–48 business hours.</p>
            </div>
          </li>
          <li className="zp-timeline-step">
            <span className="zp-timeline-marker">2</span>
            <div className="zp-timeline-content">
              <strong>Bank Account Validation</strong>
              <p>Your bank details are verified through penny drop validation for secure payouts.</p>
            </div>
          </li>
          <li className="zp-timeline-step">
            <span className="zp-timeline-marker">3</span>
            <div className="zp-timeline-content">
              <strong>Account Activation & Welcome Kit</strong>
              <p>Once approved, you will receive full account access details via SMS and email.</p>
            </div>
          </li>
        </ol>
      </div>

      {/* Navigation Buttons */}
      <div className="zp-confirm-actions">
        <LoadingButton onClick={() => navigate('/dashboard')}>
          Go to Dashboard
        </LoadingButton>
        <SecondaryButton onClick={() => navigate('/login')}>
          Back to Login
        </SecondaryButton>
      </div>

      <p className="zp-confirm-help">
        Need assistance with your application? Contact our team at{' '}
        <a href="mailto:support@zaylope.com">support@zaylope.com</a>
      </p>
    </div>
  );
}
