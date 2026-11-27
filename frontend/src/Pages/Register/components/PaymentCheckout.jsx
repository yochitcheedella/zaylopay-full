import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { User } from 'lucide-react';
import API from '../../../Services/api';
import {
  ConsentCheckbox,
  FormError,
  FormInput,
  LoadingButton,
  SecondaryButton,
  StatusMessage,
} from './FormControls';
import { VerifiedBadge } from './VerificationStatus';
import { maskEmail, maskMobile } from '../registrationState';
import { normalizeFullName, validateFullName } from '../../../utils/validation';
import './PaymentCheckout.css';

function loadRazorpayScript() {
  return new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const existing = document.querySelector(
      'script[src="https://checkout.razorpay.com/v1/checkout.js"]'
    );
    if (existing) {
      existing.addEventListener('load', () => resolve(true), { once: true });
      existing.addEventListener('error', () => resolve(false), { once: true });
      return;
    }
    const script = document.createElement('script');
    script.src = 'https://checkout.razorpay.com/v1/checkout.js';
    script.async = true;
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
}

function isUnmaskedEmail(email) {
  const clean = String(email || '').trim();
  return Boolean(
    clean && !clean.includes('•') && !clean.includes('*') && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(clean)
  );
}

function normalizeUnmaskedMobile(mobile) {
  const raw = String(mobile || '').trim();
  if (!raw || raw.includes('•') || raw.includes('*')) return '';
  const digits = raw.replace(/\D/g, '');
  if (/^[6-9]\d{9}$/.test(digits)) return digits;
  if (/^91[6-9]\d{9}$/.test(digits)) return digits.slice(2);
  return '';
}

export default function PaymentCheckout({
  applicantName = '',
  applicantEmail = '',
  applicantMobile = '',
  mobileVerified = false,
  emailVerified = false,
  emailVerificationMethod = 'NONE',
  maskedMobile = '',
  maskedEmail = '',
  registrationSubmitted = false,
  backendConsentAccepted = false,
  onConsentSubmitted,
  onNameUpdated,
  onEditContact,
  onBusyChange,
  onPaymentSuccess,
  onBack,
}) {
  const [loading, setLoading] = useState(false);
  const [checkingStatus, setCheckingStatus] = useState(true);
  const [verifying, setVerifying] = useState(false);
  const [activationPending, setActivationPending] = useState(false);
  const [error, setError] = useState('');
  const [infoNotice, setInfoNotice] = useState('');
  const [successMsg, setSuccessMsg] = useState('');
  const [simulating, setSimulating] = useState(false);
  const [confirmedAmountPaise, setConfirmedAmountPaise] = useState(59900);
  const [confirmedCurrency, setConfirmedCurrency] = useState('INR');
  const [backendPendingOrder, setBackendPendingOrder] = useState(false);

  // Submission & declaration consent state
  const [localSubmitted, setLocalSubmitted] = useState(false);
  const [declarationChecked, setDeclarationChecked] = useState(
    Boolean(registrationSubmitted && backendConsentAccepted)
  );
  const [declarationError, setDeclarationError] = useState('');

  // Inline details editor before consent submission & payment
  const [editingDetails, setEditingDetails] = useState(false);
  const [editFullName, setEditFullName] = useState(applicantName || '');
  const [editNameError, setEditNameError] = useState('');
  const [savingName, setSavingName] = useState(false);

  const initiateLockRef = useRef(false);

  const submittedState = Boolean(registrationSubmitted || localSubmitted);
  const effectiveDeclarationChecked = Boolean(
    (submittedState && backendConsentAccepted) || declarationChecked
  );
  const isEditingOpen = editingDetails && !submittedState && !activationPending;

  useEffect(() => {
    onBusyChange?.(loading || simulating || verifying);
  }, [loading, simulating, verifying, onBusyChange]);

  const contactSatisfied = Boolean(mobileVerified || emailVerified);
  const isGoogleEmailVerified = emailVerified && emailVerificationMethod === 'GOOGLE';
  const displayedMobile = maskedMobile || maskMobile(applicantMobile);
  const displayedEmail = maskedEmail || maskEmail(applicantEmail);
  const emailBadgeLabel = isGoogleEmailVerified
    ? 'Email verified with Google'
    : 'Email verified via OTP';

  const applyPaymentStatusPayload = useCallback(
    (status) => {
      if (status?.amountPaise && Number(status.amountPaise) > 0) {
        setConfirmedAmountPaise(Number(status.amountPaise));
      }
      if (status?.currency) {
        setConfirmedCurrency(status.currency);
      }

      if (status?.paid || status?.paymentStatus === 'PAID') {
        if (status?.activationPending || status?.membershipStatus === 'PENDING_ACTIVATION') {
          setActivationPending(true);
          setInfoNotice(
            "Your payment is confirmed. We're completing your membership activation."
          );
          setSuccessMsg('');
          return;
        }
        setActivationPending(false);
        setSuccessMsg('Payment verified & membership active! Opening your Affiliate ID…');
        setTimeout(() => {
          onPaymentSuccess?.(status);
        }, 500);
        return;
      }

      if (status?.paymentStatus === 'FAILED' && status?.lastFailureReason) {
        setError(status.lastFailureReason);
      }

      if (status?.orderId) {
        setBackendPendingOrder(true);
        if (!status?.lastFailureReason) {
          setInfoNotice(
            'A pending membership payment order is ready. Complete checkout or check status below.'
          );
        }
      } else {
        setBackendPendingOrder(false);
      }
    },
    [onPaymentSuccess]
  );

  const checkExistingPaymentStatus = useCallback(
    async (withSpinner = true) => {
      if (withSpinner) {
        setCheckingStatus(true);
        setError('');
      }
      try {
        const res = await API.get('/applicants/me/payments/status');
        applyPaymentStatusPayload(res.data);
      } catch (err) {
        if (err.response?.status === 401) {
          setError('Your verification timed out for security. Please log in to continue payment.');
        }
      } finally {
        setCheckingStatus(false);
      }
    },
    [applyPaymentStatusPayload]
  );

  useEffect(() => {
    let ignore = false;
    API.get('/applicants/me/payments/status')
      .then((res) => {
        if (!ignore) {
          applyPaymentStatusPayload(res.data);
        }
      })
      .catch((err) => {
        if (!ignore && err.response?.status === 401) {
          setError('Your verification timed out for security. Please log in to continue payment.');
        }
      })
      .finally(() => {
        if (!ignore) setCheckingStatus(false);
      });
    return () => {
      ignore = true;
    };
  }, [applyPaymentStatusPayload]);

  const handleSaveName = async (e) => {
    e.preventDefault();
    if (savingName || submittedState) return;
    const clean = normalizeFullName(editFullName);
    const err = validateFullName(editFullName || '');
    if (err || !clean) {
      setEditNameError(err || 'Enter your full legal name.');
      return;
    }

    setSavingName(true);
    setEditNameError('');
    try {
      const res = await API.put('/applicants/me/registration/profile', {
        fullName: clean,
      });
      onNameUpdated?.(res.data?.fullName || clean);
      setEditingDetails(false);
      setInfoNotice('Personal details updated.');
    } catch (saveErr) {
      const msg =
        saveErr.response?.data?.errors?.[0]?.message ||
        saveErr.response?.data?.detail ||
        'Could not update full name. Please check and retry.';
      setEditNameError(msg);
    } finally {
      setSavingName(false);
    }
  };

  const handleRecoveryActivate = async () => {
    if (verifying) return;
    setVerifying(true);
    setError('');
    try {
      const res = await API.post('/applicants/me/membership/activate', {});
      setActivationPending(false);
      setSuccessMsg('Lifetime membership activated! Opening your Affiliate ID…');
      setTimeout(() => {
        onPaymentSuccess?.(res.data);
      }, 450);
    } catch (err) {
      setError(
        err.response?.data?.detail ||
          'We could not finish activating your membership right now. Your payment is safe — please retry in a moment.'
      );
    } finally {
      setVerifying(false);
    }
  };

  const handleVerifyGatewayResponse = async (razorpayOrderId, razorpayPaymentId, razorpaySignature) => {
    setVerifying(true);
    setInfoNotice('');
    setError('');
    try {
      const verifyRes = await API.post('/applicants/me/payments/verify', {
        razorpayOrderId,
        razorpayPaymentId,
        razorpaySignature,
      });
      const vData = verifyRes.data;
      if (vData?.activationPending || vData?.membershipStatus === 'PENDING_ACTIVATION') {
        setActivationPending(true);
        setInfoNotice("Your payment is confirmed. We're completing your membership activation.");
        setLoading(false);
        return;
      }
      setSuccessMsg('Membership payment verified & Affiliate ID activated!');
      setTimeout(() => {
        onPaymentSuccess?.(vData);
      }, 600);
    } catch (verErr) {
      setError(
        verErr.response?.data?.detail ||
          verErr.response?.data?.message ||
          'Payment verification failed. Check payment status or retry safely.'
      );
      setLoading(false);
    } finally {
      setVerifying(false);
      setSimulating(false);
      initiateLockRef.current = false;
    }
  };

  const ensureConsentSubmitted = async () => {
    if (submittedState) {
      return true;
    }
    try {
      await API.post('/applicants/me/submit', {
        consentAccepted: true,
        privacyNoticeVersion: 'onboarding-v1',
      });
      setLocalSubmitted(true);
      onConsentSubmitted?.();
      return true;
    } catch (submitErr) {
      const code = submitErr.response?.data?.code;
      if (code === 'REGISTRATION_ALREADY_SUBMITTED') {
        setLocalSubmitted(true);
        onConsentSubmitted?.();
        return true;
      }
      throw submitErr;
    }
  };

  const handlePay = async () => {
    if (initiateLockRef.current || loading || checkingStatus || activationPending) return;

    if (!contactSatisfied) {
      setError('Verify your mobile number or email address before initiating payment.');
      return;
    }
    if (!effectiveDeclarationChecked) {
      setDeclarationError('Please accept the declaration, Terms & Conditions and Privacy Policy to continue.');
      return;
    }

    initiateLockRef.current = true;
    setLoading(true);
    setError('');
    setDeclarationError('');
    setInfoNotice('');
    setSuccessMsg('');

    try {
      await ensureConsentSubmitted();

      const orderRes = await API.post('/applicants/me/payments/create-order', {});
      const { orderId, amountPaise, currency, keyId } = orderRes.data;
      if (amountPaise && Number(amountPaise) > 0) {
        setConfirmedAmountPaise(Number(amountPaise));
      }
      if (currency) {
        setConfirmedCurrency(currency);
      }

      const isMockOrder = orderId && orderId.startsWith('order_mock_');

      if (isMockOrder) {
        setSimulating(true);
        setTimeout(async () => {
          const mockPaymentId = 'pay_test_' + Math.random().toString(36).substring(2, 12);
          const mockSignature = 'mock_sig_' + Math.random().toString(36).substring(2, 16);
          await handleVerifyGatewayResponse(orderId, mockPaymentId, mockSignature);
        }, 700);
        return;
      }

      const scriptLoaded = await loadRazorpayScript();
      if (!scriptLoaded || !window.Razorpay) {
        setError(
          'Unable to load Razorpay payment gateway. Check your internet connection or disable content blockers for checkout.razorpay.com.'
        );
        setLoading(false);
        initiateLockRef.current = false;
        return;
      }

      const safePrefill = {};
      if (applicantName && !applicantName.includes('•')) safePrefill.name = applicantName;
      if (isUnmaskedEmail(applicantEmail)) safePrefill.email = applicantEmail.trim();
      const validMobile = normalizeUnmaskedMobile(applicantMobile);
      if (validMobile) safePrefill.contact = validMobile;

      const options = {
        key: keyId,
        amount: amountPaise,
        currency: currency || 'INR',
        name: 'ZayloPe',
        description: 'ZayloPe Lifetime Membership',
        order_id: orderId,
        prefill: safePrefill,
        theme: { color: '#083d31' },
        handler: async function (response) {
          await handleVerifyGatewayResponse(
            response.razorpay_order_id,
            response.razorpay_payment_id,
            response.razorpay_signature
          );
        },
        modal: {
          ondismiss: function () {
            setBackendPendingOrder(true);
            setInfoNotice(
              'Payment window closed before completion. You can safely check payment status or retry below.'
            );
            setLoading(false);
            initiateLockRef.current = false;
          },
        },
      };

      const razorpay = new window.Razorpay(options);
      razorpay.on('payment.failed', function () {
        const desc = 'Your payment did not complete. Please check your payment status before trying again.';
        setError(desc);
        setBackendPendingOrder(true);
        setLoading(false);
        initiateLockRef.current = false;
      });
      razorpay.open();
    } catch (err) {
      if (err.response?.data?.code === 'PAYMENT_ALREADY_COMPLETED') {
        await checkExistingPaymentStatus(true);
      } else {
        setError(
          err.response?.data?.detail ||
            err.response?.data?.message ||
            'Failed to initiate payment. Please retry.'
        );
      }
      setLoading(false);
      initiateLockRef.current = false;
    }
  };

  const amountRupees = Math.round(confirmedAmountPaise / 100);
  const formattedAmount = `₹${amountRupees}`;
  const baseFee = (amountRupees / 1.18).toFixed(2);
  const gstFee = (amountRupees - Number(baseFee)).toFixed(2);

  const consentLocked = submittedState && Boolean(backendConsentAccepted || declarationChecked);
  const canInitiatePayment =
    contactSatisfied &&
    effectiveDeclarationChecked &&
    !loading &&
    !checkingStatus &&
    !verifying &&
    !isEditingOpen &&
    !activationPending;

  return (
    <div className="zp-payment-checkout-container">
      {error && <FormError error={error} onDismiss={() => setError('')} />}
      {infoNotice && <StatusMessage kind="info">{infoNotice}</StatusMessage>}
      {verifying && (
        <StatusMessage kind="info">
          Verifying your payment securely with ZayloPe servers…
        </StatusMessage>
      )}
      {successMsg && <StatusMessage kind="success">{successMsg}</StatusMessage>}

      {/* Compact Applicant Details & Secure Pre-Payment Edit Controls */}
      <section className="zp-payment-applicant-box" aria-label="Applicant details">
        <div className="zp-payment-applicant-head">
          <div>
            <span className="zp-payment-applicant-kicker">Applicant details</span>
            <p className="zp-payment-applicant-name">{applicantName || 'Applicant'}</p>
          </div>
          {!submittedState && !activationPending && (
            <button
              type="button"
              className="zp-payment-edit-btn"
              disabled={loading || verifying || savingName}
              onClick={() => {
                setEditFullName(applicantName || '');
                setEditNameError('');
                setEditingDetails((prev) => !prev);
              }}
            >
              {isEditingOpen ? 'Close editor' : 'Edit details'}
            </button>
          )}
        </div>

        {!isEditingOpen ? (
          <div className="zp-payment-contacts-grid">
            <div className="zp-payment-contact-cell">
              <span className="zp-payment-contact-label">Mobile</span>
              <span className="zp-payment-contact-val">{displayedMobile || '—'}</span>
              {mobileVerified ? (
                <VerifiedBadge label="Mobile verified via OTP" />
              ) : (
                <span className="zp-badge-unverified">Not verified</span>
              )}
            </div>
            <div className="zp-payment-contact-cell">
              <span className="zp-payment-contact-label">Email</span>
              <span className="zp-payment-contact-val">{displayedEmail || '—'}</span>
              {emailVerified ? (
                <VerifiedBadge label={emailBadgeLabel} />
              ) : (
                <span className="zp-badge-unverified">Not verified</span>
              )}
            </div>
          </div>
        ) : (
          <form className="zp-payment-inline-edit" onSubmit={handleSaveName} noValidate>
            <FormInput
              id="payment-edit-full-name"
              label="Full name"
              name="edit_full_name"
              leadingIcon={<User size={18} strokeWidth={1.9} />}
              value={editFullName}
              onChange={(e) => {
                setEditFullName(e.target.value);
                setEditNameError('');
              }}
              placeholder="Enter your full legal name"
              required
              disabled={savingName}
              valid={Boolean(editFullName.trim()) && !validateFullName(editFullName)}
              error={editNameError}
            />
            <div className="zp-payment-edit-actions">
              <LoadingButton type="submit" loading={savingName} loadingLabel="Saving…">
                Save name
              </LoadingButton>
              {onEditContact && (
                <div className="zp-payment-contact-links">
                  <button
                    type="button"
                    className="zp-payment-link-action"
                    onClick={() => onEditContact('mobile')}
                    disabled={savingName}
                  >
                    {mobileVerified ? 'Change mobile number' : 'Verify / change mobile'}
                  </button>
                  <button
                    type="button"
                    className="zp-payment-link-action"
                    onClick={() => onEditContact('email')}
                    disabled={savingName}
                  >
                    {emailVerified ? 'Change email address' : 'Verify / change email'}
                  </button>
                </div>
              )}
            </div>
          </form>
        )}
      </section>

      <div className="zp-payment-card">
        <div className="zp-payment-summary-box">
          <div className="zp-payment-badge">Lifetime membership • One-time</div>
          <h2 className="zp-payment-title">ZayloPe lifetime membership</h2>
          <p className="zp-payment-desc">
            Complete your verified ₹599 one-time payment to activate your lifetime membership and
            receive your permanent unique Affiliate ID immediately.
          </p>

          <div className="zp-payment-breakdown">
            <div className="zp-breakdown-row">
              <span>Membership fee (excl. GST)</span>
              <span>₹{baseFee}</span>
            </div>
            <div className="zp-breakdown-row">
              <span>Applicable GST (18% included)</span>
              <span>₹{gstFee}</span>
            </div>
            <div className="zp-breakdown-row zp-breakdown-total">
              <span>Total payable ({confirmedCurrency})</span>
              <strong>{formattedAmount}</strong>
            </div>
          </div>
        </div>

        <ul className="zp-benefits-list">
          <li className="zp-benefit-item">
            <svg className="zp-benefit-check" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>Permanent ZayloPe Affiliate ID issued automatically after verified payment</span>
          </li>
          <li className="zp-benefit-item">
            <svg className="zp-benefit-check" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>Verified digital lifetime membership card and affiliate referral credentials</span>
          </li>
          <li className="zp-benefit-item">
            <svg className="zp-benefit-check" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path
                fillRule="evenodd"
                d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
                clipRule="evenodd"
              />
            </svg>
            <span>Automatic membership activation immediately after verified payment</span>
          </li>
        </ul>

        {!activationPending && (
          <ConsentCheckbox
            id="payment-declaration-checkbox"
            checked={effectiveDeclarationChecked}
            disabled={loading || verifying || consentLocked}
            error={declarationError}
            onChange={(e) => {
              setDeclarationChecked(e.target.checked);
              if (e.target.checked) setDeclarationError('');
            }}
          >
            I hereby declare that the personal details provided are authentic, true and accurate, and
            I accept the{' '}
            <Link to="/about" className="zp-consent-link">
              Terms &amp; Conditions
            </Link>{' '}
            and{' '}
            <Link to="/about" className="zp-consent-link">
              Privacy Policy
            </Link>
            . I understand that my ZayloPe lifetime membership and permanent Affiliate ID activate
            automatically once the ₹599 one-time payment is verified.
          </ConsentCheckbox>
        )}

        <div className="zp-gateway-notice">
          <svg className="zp-lock-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path
              fillRule="evenodd"
              d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z"
              clipRule="evenodd"
            />
          </svg>
          <span>Secured with Razorpay 256-bit encryption. UPI, Cards, NetBanking supported.</span>
        </div>

        <div className="zp-payment-actions">
          {onBack && !activationPending && !submittedState && (
            <SecondaryButton disabled={loading || verifying} onClick={onBack}>
              Back
            </SecondaryButton>
          )}
          {activationPending ? (
            <LoadingButton
              loading={verifying}
              loadingLabel="Completing activation…"
              onClick={handleRecoveryActivate}
              className="zp-pay-button"
            >
              Complete Membership Activation
            </LoadingButton>
          ) : (
            <LoadingButton
              loading={loading || checkingStatus || verifying}
              loadingLabel={
                checkingStatus
                  ? 'Checking payment…'
                  : verifying || simulating
                    ? 'Verifying payment…'
                    : 'Opening Razorpay…'
              }
              disabled={!canInitiatePayment}
              onClick={handlePay}
              className="zp-pay-button"
            >
              {`Pay ${formattedAmount}`}
            </LoadingButton>
          )}
        </div>

        {backendPendingOrder && !loading && !activationPending && (
          <SecondaryButton
            type="button"
            disabled={checkingStatus}
            onClick={() => checkExistingPaymentStatus(true)}
            className="zp-payment-reconcile-btn"
          >
            Check payment status
          </SecondaryButton>
        )}
      </div>
    </div>
  );
}

