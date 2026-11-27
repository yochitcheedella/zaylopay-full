import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { User, Smartphone, Mail } from 'lucide-react';
import API from '../../Services/api';

// Design Tokens & Shared Unified Layout
import './registrationTokens.css';
import './Register.css';
import RegistrationLayout from './components/RegistrationLayout';

// Shared Reusable Components
import {
  FormError,
  FormInput,
  PhoneInput,
  LoadingButton,
  SecondaryButton,
  StatusMessage,
  MembershipSummary,
} from './components/FormControls';
import OtpInput from './components/OtpInput';
import VerificationMethodSelector from './components/VerificationMethodSelector';
import { ChannelVerifiedCard } from './components/VerificationStatus';
import PaymentCheckout from './components/PaymentCheckout';
import ApprovalStatus from './components/ApprovalStatus';
import GoogleAuthButton from '../../Components/Auth/GoogleAuthButton';
import GoogleAccountCard from '../../Components/Auth/GoogleAccountCard';
import AuthDivider from '../../Components/Auth/AuthDivider';

// State & Validation Helpers
import {
  maskEmail,
  maskMobile,
  personalFieldErrors,
  restorationNotice,
} from './registrationState';
import {
  extractNationalIndianMobile,
  getEmailDomainSuggestion,
  normalizeEmail,
  normalizeFullName,
  validateRegistrationEmail,
  validateEmail,
  validateFullName,
  validateMobile,
} from '../../utils/validation';
import { resolvePostAuthRoute } from '../../utils/authRedirect';

const BACKEND_TO_FORM_FIELD = {
  fullName: 'full_name',
  mobileNumber: 'mobile_number',
  email: 'email',
};

function payloadContactUnchanged(value, current, channel) {
  const normalize = channel === 'mobile' ? extractNationalIndianMobile : normalizeEmail;
  const normalized = normalize(value);
  return Boolean(normalized && normalized === normalize(current));
}

function extractBackendFieldErrors(data) {
  const mapped = {};
  if (Array.isArray(data?.errors)) {
    for (const err of data.errors) {
      const formField = BACKEND_TO_FORM_FIELD[err?.field];
      if (formField && err?.message && !mapped[formField]) {
        mapped[formField] = err.message;
      }
    }
  }
  return mapped;
}

const STEP_LABELS = ['Account', 'Verify', 'Payment', 'Status'];
const COMPACT_STEP_LABELS = ['Account', 'Verify', 'Pay', 'Status'];

export default function Register() {
  const [emailPolicy, setEmailPolicy] = useState(null);
  const [emailPolicyError, setEmailPolicyError] = useState('');
  const [emailPolicyAttempt, setEmailPolicyAttempt] = useState(0);
  useEffect(() => {
    let active = true;
    API.get('/validation/email-policy').then(({ data }) => {
      if (!Array.isArray(data?.allowedDomains) || !Array.isArray(data?.disposableDomains)) {
        throw new Error('Invalid email policy response');
      }
      if (active) { setEmailPolicy(data); setEmailPolicyError(''); }
    }).catch(() => {
      if (active) setEmailPolicyError('Email validation is temporarily unavailable. Please retry. Your entries are preserved.');
    });
    return () => { active = false; };
  }, [emailPolicyAttempt]);
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();

  // ==========================================
  // NAVIGATION & STEP GATING (4 STEPS)
  // ==========================================
  const hasExplicitStepParam = searchParams.has('step');
  const requestedParam = Number(searchParams.get('step'));
  const requestedStep =
    requestedParam === 5
      ? 4
      : Number.isInteger(requestedParam) && requestedParam >= 1 && requestedParam <= 4
        ? requestedParam
        : 1;

  const [applicantId, setApplicantId] = useState(null);
  const [, setApplicantReference] = useState('');
  const [registrationSubmitted, setRegistrationSubmitted] = useState(false);
  const [backendConsentAccepted, setBackendConsentAccepted] = useState(false);
  const [hasPendingOrder, setHasPendingOrder] = useState(false);
  const [paymentCompleted, setPaymentCompleted] = useState(false);
  const [onboardingStatus, setOnboardingStatus] = useState(null);

  // ==========================================
  // STEP 1: MANUAL FORM & GOOGLE ONBOARDING
  // ==========================================
  const [formData, setFormData] = useState({
    full_name: '',
    mobile_number: '',
    email: '',
  });
  const [touched, setTouched] = useState({});
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [serverErrors, setServerErrors] = useState({});
  const [inputRestrictionErrors, setInputRestrictionErrors] = useState({});
  const [googleLoading, setGoogleLoading] = useState(false);
  const [googleNotice, setGoogleNotice] = useState('');

  // Input refs for focus management on unsuccessful submit
  const fullNameInputRef = useRef(null);
  const mobileInputRef = useRef(null);
  const emailInputRef = useRef(null);
  const googleNameInputRef = useRef(null);
  const googleMobileInputRef = useRef(null);

  // Server-backed Google onboarding flows
  const [googleOnboarding, setGoogleOnboarding] = useState(null); // { email, fullName, pictureUrl, authoritativeEmail }
  const [googleLinking, setGoogleLinking] = useState(null); // { email, linkingChannel, maskedTarget, pictureUrl }
  const [linkChallengeSent, setLinkChallengeSent] = useState(false);
  const [linkOtpDigits, setLinkOtpDigits] = useState(Array(6).fill(''));
  const [linkOtpError, setLinkOtpError] = useState('');
  const [linkOtpMessage, setLinkOtpMessage] = useState('');
  const [linkRetryAt, setLinkRetryAt] = useState(0);
  const [linkBusy, setLinkBusy] = useState(false);

  // ==========================================
  // STEP 2: CONDITIONAL OTP VERIFICATION STATE
  // ==========================================
  const [verificationState, setVerificationState] = useState(null);
  const [selectedChannel, setSelectedChannel] = useState('mobile'); // 'mobile' | 'email'
  const [mobileVerified, setMobileVerified] = useState(false);
  const [emailVerified, setEmailVerified] = useState(false);
  const [emailVerificationMethod, setEmailVerificationMethod] = useState('NONE');
  const [needsIndependentVerificationNotice, setNeedsIndependentVerificationNotice] =
    useState(false);

  const [otpDigits, setOtpDigits] = useState(Array(6).fill(''));
  const [otpBusy, setOtpBusy] = useState(''); // 'send' | 'verify' | 'switch' | 'change-contact'
  const [otpError, setOtpError] = useState('');
  const [otpMessage, setOtpMessage] = useState('');
  const [otpVerifiedBanner, setOtpVerifiedBanner] = useState(false);
  const [activeChallenge, setActiveChallenge] = useState(null);
  const [retryAt, setRetryAt] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const [online, setOnline] = useState(() => navigator.onLine);

  // Inline contact editor inside Step 2
  const [editingContactChannel, setEditingContactChannel] = useState(''); // '' | 'mobile' | 'email'
  const [contactEditValue, setContactEditValue] = useState('');
  const [contactEditError, setContactEditError] = useState('');
  const [contactEditTouched, setContactEditTouched] = useState(false);


  // ==========================================
  // GENERAL & RESTORATION STATE
  // ==========================================
  const [restoring, setRestoring] = useState(true);
  const [loading, setLoading] = useState(false);
  const [paymentBusy, setPaymentBusy] = useState(false);
  const [generalError, setGeneralError] = useState('');
  const [restoreNotice, setRestoreNotice] = useState(null);
  const submitLock = useRef(false);
  const restoreInFlight = useRef(false);
  const didInitialRestore = useRef(false);

  const contactVerified = mobileVerified || emailVerified;

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const handleOffline = () => setOnline(false);
    const handleOnline = () => setOnline(true);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('online', handleOnline);
    return () => {
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('online', handleOnline);
    };
  }, []);

  const maxAllowedStep = paymentCompleted
    ? 4
    : hasPendingOrder
      ? 4
      : applicantId && (contactVerified || registrationSubmitted)
        ? 3
        : applicantId
          ? 2
          : 1;

  let currentStep = Math.min(requestedStep, maxAllowedStep);
  if (paymentCompleted) {
    currentStep = 4;
  } else if (registrationSubmitted && currentStep < 3) {
    currentStep = 3;
  }

  const pendingDeliveryChannel = activeChallenge?.channel;
  const pendingDeliveryExpiresAt = activeChallenge?.expiresAt;
  useEffect(() => {
    if (!pendingDeliveryChannel || currentStep !== 2) return undefined;
    let active = true;
    let checking = false;
    let checks = 0;
    const timer = setInterval(async () => {
      if (checking) return;
      if (++checks >= 6) clearInterval(timer);
      checking = true;
      try {
        const { data } = await API.get('/applicants/me/verification/status');
        if (active && data?.[pendingDeliveryChannel]?.challengeStatus === 'DELIVERY_FAILED') {
          setVerificationState(data);
          setActiveChallenge(null);
          setOtpDigits(Array(6).fill(''));
          setOtpMessage('');
          setOtpError('Code delivery failed. Check your contact details and retry after the resend timer.');
        }
      } catch {
        // Keep the submitted challenge and existing retry controls through temporary network failures.
      } finally { checking = false; }
    }, 3000);
    return () => { active = false; clearInterval(timer); };
  }, [pendingDeliveryChannel, pendingDeliveryExpiresAt, currentStep]);

  const goToStep = useCallback(
    (targetStep, replace = false, overrideMaxStep = null) => {
      const effectiveMax =
        overrideMaxStep !== null ? Math.max(maxAllowedStep, overrideMaxStep) : maxAllowedStep;
      let clampedTarget = Math.min(Math.max(1, targetStep), effectiveMax);
      if (paymentCompleted) {
        clampedTarget = 4;
      } else if (registrationSubmitted && overrideMaxStep === null && clampedTarget < 3) {
        clampedTarget = 3;
      }

      setGeneralError('');
      setEditingContactChannel('');
      setContactEditError('');
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('step', String(clampedTarget));
          return next;
        },
        { replace }
      );
    },
    [maxAllowedStep, paymentCompleted, registrationSubmitted, setSearchParams]
  );

  useEffect(() => {
    if (restoring) return;
    if (hasExplicitStepParam && requestedParam !== currentStep) {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev);
          next.set('step', String(currentStep));
          return next;
        },
        { replace: true }
      );
    }
  }, [restoring, hasExplicitStepParam, requestedParam, currentStep, setSearchParams]);

  const hydrateVerificationAndProgress = useCallback(
    async (sessionOrAuthData, preferredStep = null) => {
      const verRes = await API.get('/applicants/me/verification/status');
      const verData = verRes.data;
      setVerificationState(verData);

      const isMobVerified =
        verData.mobile?.verified === true || sessionOrAuthData?.mobileVerified === true;
      const isEmailVer =
        verData.email?.verified === true || sessionOrAuthData?.emailVerified === true;
      const method =
        sessionOrAuthData?.emailVerificationMethod || (isEmailVer ? 'EMAIL_OTP' : 'NONE');

      setMobileVerified(isMobVerified);
      setEmailVerified(isEmailVer);
      setEmailVerificationMethod(method);

      if (verData.email?.challengeStatus === 'ACTIVE' && verData.mobile?.challengeStatus !== 'ACTIVE') {
        setSelectedChannel('email');
      } else if (!verData.mobile?.enabled && verData.email?.enabled) {
        setSelectedChannel('email');
      } else {
        setSelectedChannel('mobile');
      }

      try {
        const regRes = await API.get('/applicants/me/registration');
        const regData = regRes.data;
        setFormData((prev) => ({
          ...prev,
          full_name: regData.fullName || prev.full_name,
          mobile_number: regData.maskedMobileNumber || prev.mobile_number,
          email: regData.maskedEmailAddress || prev.email,
        }));
        if (regData.applicantReference) {
          setApplicantReference(regData.applicantReference);
        }
        if (regData.emailVerificationMethod) {
          setEmailVerificationMethod(regData.emailVerificationMethod);
        }
        setBackendConsentAccepted(Boolean(regData.consentAccepted));

        if (regData.registrationStatus === 'SUBMITTED' || sessionOrAuthData?.registrationSubmitted) {
          setRegistrationSubmitted(true);
          try {
            const statusRes = await API.get('/applicants/me/membership/status');
            const mStatus = statusRes.data;
            if (mStatus) {
              setOnboardingStatus(mStatus);
              if (mStatus.registrationId) {
                setApplicantReference(mStatus.registrationId);
              }
              if (
                mStatus.paymentStatus === 'PAID' ||
                sessionOrAuthData?.paymentStatus === 'PAID'
              ) {
                setPaymentCompleted(true);
                goToStep(4, true, 4);
              } else if (mStatus.nextDestination === 'PAYMENT_STATUS') {
                setHasPendingOrder(true);
                const explicitStep = Number(new URLSearchParams(window.location.search).get('step'));
                goToStep(explicitStep === 3 ? 3 : (preferredStep || 4), true, 4);
              } else {
                goToStep(3, true, 3);
              }
            } else {
              goToStep(3, true, 3);
            }
          } catch {
            goToStep(3, true, 3);
          }
          return;
        }
      } catch {
        // Proceed cleanly with verification-derived step
      }

      if (preferredStep) {
        goToStep(preferredStep, preferredStep >= 3, preferredStep);
      } else if (!new URLSearchParams(window.location.search).has('step')) {
        if (isMobVerified || isEmailVer) {
          goToStep(3, true, 3);
        } else {
          goToStep(2, true, 2);
        }
      }
    },
    [goToStep]
  );

  // ==========================================
  // RESTORE SAVED DRAFT OR GOOGLE ONBOARDING ON LOAD
  // ==========================================
  const restoreDraft = useCallback(async () => {
    if (restoreInFlight.current) return;
    restoreInFlight.current = true;
    setRestoring(true);
    setRestoreNotice(null);

    try {
      const sessionRes = await API.get('/auth/session');
      const sessionData = sessionRes.data;

      if (!sessionData?.authenticated || sessionData.sessionState !== 'ACTIVE') {
        setApplicantId(null);
        setMobileVerified(false);
        setEmailVerified(false);
        setEmailVerificationMethod('NONE');
        setRegistrationSubmitted(false);
        setBackendConsentAccepted(false);
        setHasPendingOrder(false);
        setPaymentCompleted(false);

        if (sessionData?.onboarding?.active) {
          try {
            const obRes = await API.get('/auth/google/onboarding');
            const ob = obRes.data;
            if (ob?.status === 'NEEDS_PROFILE_COMPLETION') {
              setGoogleLinking(null);
              setGoogleOnboarding({
                email: ob.email,
                fullName: ob.fullName || '',
                pictureUrl: ob.pictureUrl || sessionData.onboarding.pictureUrl || null,
                authoritativeEmail: Boolean(ob.authoritativeEmail),
              });
              setFormData({
                full_name: ob.fullName || '',
                mobile_number: '',
                email: ob.email || '',
              });
              setTouched(
                ob.fullName && validateFullName(ob.fullName) ? { full_name: true } : {}
              );
              setSubmitAttempted(false);
              setServerErrors({});
            } else if (ob?.status === 'LINKING_CONFIRMATION_REQUIRED') {
              setGoogleOnboarding(null);
              setGoogleLinking({
                email: ob.email,
                linkingChannel: ob.linkingChannel,
                maskedTarget: ob.maskedMobileNumber || ob.maskedEmailAddress,
                pictureUrl: ob.pictureUrl || sessionData.onboarding.pictureUrl || null,
              });
            }
          } catch {
            setGoogleOnboarding(null);
            setGoogleLinking(null);
            setGeneralError(
              'Your previous Google sign-in timed out for your security. Please continue with Google again or register manually below.'
            );
          }
        } else {
          setGoogleOnboarding(null);
          setGoogleLinking(null);
        }

        setRestoring(false);
        restoreInFlight.current = false;
        return;
      }

      if (
        sessionData.eligibleStep === 'RESTRICTED' ||
        sessionData.eligibleStep === 'ACTIVATION_RECOVERY'
      ) {
        setRegistrationSubmitted(true);
        setPaymentCompleted(true);
        goToStep(4, true, 4);
        setRestoring(false);
        restoreInFlight.current = false;
        return;
      }

      const urlStep = Number.parseInt(new URLSearchParams(window.location.search).get('step') || '', 10);
      if (
        (sessionData.canAccessDashboard || sessionData.eligibleStep === 'DASHBOARD') &&
        urlStep !== 4 &&
        urlStep !== 5
      ) {
        navigate(resolvePostAuthRoute('DASHBOARD'), { replace: true });
        return;
      }

      if (sessionData.eligibleStep === 'PAYMENT_STATUS') {
        setHasPendingOrder(true);
      }

      setGoogleOnboarding(null);
      setGoogleLinking(null);

      const ref = sessionData.applicantReference || 'APP-ACTIVE';
      setApplicantId(ref);
      setApplicantReference(ref);
      setFormData((prev) => ({
        ...prev,
        full_name: sessionData.fullName || prev.full_name,
        mobile_number: sessionData.maskedMobileNumber || prev.mobile_number,
        email: sessionData.maskedEmailAddress || prev.email,
      }));
      if (sessionData.legacyEmailUnverified) {
        setNeedsIndependentVerificationNotice(true);
      }

      await hydrateVerificationAndProgress(sessionData);
      setRestoreNotice(null);
    } catch (failure) {
      if (failure.response?.status === 401) {
        setApplicantId(null);
        setMobileVerified(false);
        setEmailVerified(false);
        setRegistrationSubmitted(false);
        setPaymentCompleted(false);
      } else {
        const notice = restorationNotice(failure, navigator.onLine);
        if (notice) setRestoreNotice(notice);
      }
    } finally {
      setRestoring(false);
      restoreInFlight.current = false;
    }
  }, [goToStep, hydrateVerificationAndProgress, navigate]);

  useEffect(() => {
    if (didInitialRestore.current) return;
    didInitialRestore.current = true;
    restoreDraft();
  }, [restoreDraft]);

  const applyAuthenticatedGoogleOutcome = useCallback(
    async (data) => {
      if (data.eligibleStep === 'DASHBOARD') {
        navigate(resolvePostAuthRoute('DASHBOARD'), { replace: true });
        return;
      }

      const ref = data.applicantReference || data.affiliateId || 'APP-GOOGLE';
      setApplicantId(ref);
      setApplicantReference(ref);
      setGoogleOnboarding(null);
      setGoogleLinking(null);
      setFormData((prev) => ({
        ...prev,
        full_name: data.fullName || prev.full_name,
        mobile_number: data.maskedMobileNumber || prev.mobile_number,
        email: data.maskedEmailAddress || prev.email,
      }));
      setNeedsIndependentVerificationNotice(
        data.eligibleStep === 'VERIFY' && data.emailVerificationMethod === 'NONE'
      );

      let preferredStep = 2;
      if (
        data.eligibleStep === 'STATUS' ||
        data.eligibleStep === 'ACTIVATION_RECOVERY' ||
        data.eligibleStep === 'RESTRICTED'
      ) {
        setRegistrationSubmitted(true);
        setPaymentCompleted(true);
        preferredStep = 4;
      } else if (data.eligibleStep === 'PAYMENT_STATUS') {
        setHasPendingOrder(true);
        preferredStep = 4;
      } else if (data.eligibleStep === 'PAYMENT' || data.eligibleStep === 'REVIEW') {
        if (data.registrationSubmitted) {
          setRegistrationSubmitted(true);
        }
        preferredStep = 3;
      } else if (data.eligibleStep === 'ACCOUNT') {
        preferredStep = 1;
      }

      await hydrateVerificationAndProgress(data, preferredStep);
    },
    [hydrateVerificationAndProgress, navigate]
  );

  // ==========================================
  // LIVE CLIENT VALIDATION (SHOWN AFTER BLUR OR SUBMIT)
  // ==========================================
  const liveFieldErrors = personalFieldErrors({
    ...formData,
    email: googleOnboarding ? googleOnboarding.email : formData.email,
  }, emailPolicy);
  const emailSuggestion =
    (submitAttempted || touched.email) && !googleOnboarding
      ? getEmailDomainSuggestion(formData.email)
      : null;

  const getFieldError = (field) => {
    if (serverErrors[field]) return serverErrors[field];
    if (inputRestrictionErrors[field]) return inputRestrictionErrors[field];
    if (submitAttempted || touched[field]) {
      // Availability is shown by the retry banner, not as an invalid email format.
      if (field === 'email' && !emailPolicy) return validateEmail(formData.email);
      return liveFieldErrors[field] || '';
    }
    return '';
  };

  const isFieldFormatValid = (field) => {
    const val = (formData[field] || '').trim();
    if (!val || serverErrors[field] || inputRestrictionErrors[field]) return false;
    return !liveFieldErrors[field] && (field !== 'email' || (Boolean(emailPolicy) && !loading));
  };

  const handleFieldBlur = (field) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
  };

  const handlePersonalChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    setServerErrors((prev) => ({ ...prev, [name]: undefined }));
    setInputRestrictionErrors((prev) => ({ ...prev, [name]: undefined }));
    if (generalError) setGeneralError('');
  };

  // ==========================================
  // STEP 1 HANDLERS: GOOGLE SIGN-IN, PROFILE COMPLETION & LINKING
  // ==========================================
  const handleGoogleSuccess = async (credential) => {
    setGoogleLoading(true);
    setGeneralError('');
    setGoogleNotice('');
    setServerErrors({});
    setTouched({});
    setSubmitAttempted(false);

    try {
      const response = await API.post('/auth/google', { credential });
      const data = response.data;

      if (data.status === 'AUTHENTICATED') {
        await applyAuthenticatedGoogleOutcome(data);
      } else if (data.status === 'NEEDS_PROFILE_COMPLETION') {
        setGoogleLinking(null);
        setGoogleOnboarding({
          email: data.email,
          fullName: data.fullName || '',
          pictureUrl: data.pictureUrl || null,
          authoritativeEmail: Boolean(data.authoritativeEmail),
        });
        setFormData({
          full_name: data.fullName || '',
          mobile_number: '',
          email: data.email || '',
        });
        setTouched(
          data.fullName && validateFullName(data.fullName) ? { full_name: true } : {}
        );
      } else if (data.status === 'LINKING_CONFIRMATION_REQUIRED') {
        setGoogleOnboarding(null);
        setGoogleLinking({
          email: data.email,
          linkingChannel: data.linkingChannel,
          maskedTarget: data.maskedMobileNumber || data.maskedEmailAddress,
          pictureUrl: data.pictureUrl || null,
        });
        setLinkChallengeSent(false);
        setLinkOtpDigits(Array(6).fill(''));
        setLinkOtpError('');
        setLinkOtpMessage('');
      }
    } catch (err) {
      const status = err.response?.status;
      const code = err.response?.data?.code;
      if (status === 503 || code === 'GOOGLE_AUTH_UNAVAILABLE') {
        setGeneralError(
          'Google sign-in is temporarily unavailable. You can still register with your mobile number and email below.'
        );
      } else if (status === 401 || code === 'INVALID_GOOGLE_TOKEN') {
        setGeneralError(
          'We could not verify your Google account. Please sign in with Google again or register manually.'
        );
      } else if (code === 'GOOGLE_IDENTITY_CONFLICT') {
        setGeneralError(
          err.response?.data?.detail ||
          'This account is already connected to another Google account.'
        );
      } else if (!err.response) {
        setGeneralError('Unable to reach ZayloPe servers. Please check your internet connection.');
      } else {
        setGeneralError(
          err.response?.data?.detail ||
          'Google sign-in could not be completed. Please try again or register manually below.'
        );
      }
    } finally {
      setGoogleLoading(false);
    }
  };

  const handleGoogleError = (errorMsg) => {
    setGoogleNotice(
      errorMsg && errorMsg !== 'No credential returned from Google.'
        ? `${errorMsg} You can still register manually below.`
        : 'Google sign-in was cancelled. You can try again or register manually below.'
    );
  };

  const handleUseDifferentGoogleAccount = async () => {
    setLoading(true);
    setGeneralError('');
    setGoogleNotice('');
    try {
      await API.post('/auth/google/cancel', {});
    } catch {
      // Best-effort clear
    } finally {
      setGoogleOnboarding(null);
      setGoogleLinking(null);
      setLinkChallengeSent(false);
      setLinkOtpDigits(Array(6).fill(''));
      setLinkOtpError('');
      setLinkOtpMessage('');
      setServerErrors({});
      setTouched({});
      setSubmitAttempted(false);
      setFormData({ full_name: '', mobile_number: '', email: '' });
      setLoading(false);
    }
  };

  const handleGoogleProfileSubmit = async (e) => {
    e.preventDefault();
    if (loading || submitLock.current || !online || !emailPolicy) return;

    setSubmitAttempted(true);
    const errors = personalFieldErrors({
      ...formData,
      email: googleOnboarding?.email || formData.email,
    }, emailPolicy);
    if (inputRestrictionErrors.mobile_number) {
      errors.mobile_number = inputRestrictionErrors.mobile_number;
    }

    if (Object.keys(errors).length > 0) {
      setGeneralError('Please check the highlighted fields before continuing.');
      if (errors.full_name) googleNameInputRef.current?.focus();
      else if (errors.mobile_number) googleMobileInputRef.current?.focus();
      return;
    }

    setLoading(true);
    setGeneralError('');
    setServerErrors({});
    submitLock.current = true;

    try {
      const response = await API.post('/auth/google/complete-profile', {
        fullName: normalizeFullName(formData.full_name) || formData.full_name.trim(),
        mobileNumber:
          extractNationalIndianMobile(formData.mobile_number) || formData.mobile_number.trim(),
      });
      await applyAuthenticatedGoogleOutcome(response.data);
    } catch (err) {
      const data = err.response?.data;
      if (err.response?.status === 401) {
        setGoogleOnboarding(null);
        setGeneralError(
          'Your sign-in timed out for your security. Please sign in with Google again to continue.'
        );
      } else if (data?.code === 'VALIDATION_FAILED') {
        const mapped = extractBackendFieldErrors(data);
        if (Object.keys(mapped).length > 0) {
          setServerErrors(mapped);
          setGeneralError('Please check the highlighted fields before continuing.');
          if (mapped.full_name) googleNameInputRef.current?.focus();
          else if (mapped.mobile_number) googleMobileInputRef.current?.focus();
        } else {
          setGeneralError(data?.detail || 'Please check your entries and try again.');
        }
      } else if (
        data?.code === 'MOBILE_NUMBER_ALREADY_IN_USE' ||
        data?.code === 'MOBILE_ALREADY_VERIFIED'
      ) {
        setServerErrors({
          mobile_number: 'This mobile number is already registered. Log in to continue.',
        });
        googleMobileInputRef.current?.focus();
      } else if (data?.code === 'INVALID_MOBILE_NUMBER') {
        setServerErrors({ mobile_number: 'Enter a valid 10-digit mobile number.' });
        googleMobileInputRef.current?.focus();
      } else if (data?.code === 'EMAIL_ALREADY_REGISTERED' || data?.code === 'GOOGLE_IDENTITY_CONFLICT') {
        setGeneralError(
          'An account with this email or Google profile already exists. Please log in instead.'
        );
      } else if (!err.response) {
        setGeneralError('You appear to be offline. Reconnect to continue — your entries stay on this page.');
      } else {
        setGeneralError(data?.detail || 'Could not complete your profile. Please try again.');
      }
    } finally {
      setLoading(false);
      submitLock.current = false;
    }
  };

  const handleSendLinkingOtp = async () => {
    if (linkBusy || !online) return;
    setLinkBusy(true);
    setLinkOtpError('');
    setLinkOtpMessage('');

    try {
      const res = await API.post('/auth/google/link/send-otp', {});
      const data = res.data;
      setLinkChallengeSent(true);
      setLinkOtpDigits(Array(6).fill(''));
      setLinkRetryAt(Date.now() + Number(data.resendAfterSeconds || 60) * 1000);
      setLinkOtpMessage(
        data.deliveryMode === 'MOCK'
          ? `Test verification code generated for ${data.maskedDestination}. Check backend terminal logs.`
          : `A 6-digit verification code was sent to ${data.maskedDestination}.`
      );
    } catch (err) {
      const retry = Number(err.response?.data?.retryAfterSeconds || 0);
      if (retry > 0) setLinkRetryAt(Date.now() + retry * 1000);
      if (err.response?.status === 401) {
        setGoogleLinking(null);
        setGeneralError('Your sign-in timed out for security. Please continue with Google again.');
      } else {
        setLinkOtpError(
          err.response?.data?.detail || 'Could not send verification code. Please retry.'
        );
      }
    } finally {
      setLinkBusy(false);
    }
  };

  const handleVerifyLinkingOtp = async (e) => {
    e.preventDefault();
    const code = linkOtpDigits.join('');
    if (linkBusy || !online || code.length !== 6) return;

    setLinkBusy(true);
    setLinkOtpError('');

    try {
      const res = await API.post('/auth/google/link/verify', { otpCode: code });
      await applyAuthenticatedGoogleOutcome(res.data);
    } catch (err) {
      if (err.response?.status === 401) {
        setGoogleLinking(null);
        setGeneralError('Your sign-in timed out for security. Please continue with Google again.');
      } else {
        setLinkOtpError(
          err.response?.data?.detail || 'That code is invalid or expired. Please try again.'
        );
      }
    } finally {
      setLinkBusy(false);
    }
  };

  const handlePersonalSubmit = async (e) => {
    e.preventDefault();
    if (loading || submitLock.current || !online || !emailPolicy) return;

    if (applicantId) {
      goToStep(maxAllowedStep > 1 ? maxAllowedStep : 2);
      return;
    }

    setSubmitAttempted(true);
    const errors = personalFieldErrors(formData, emailPolicy);
    if (inputRestrictionErrors.mobile_number) {
      errors.mobile_number = inputRestrictionErrors.mobile_number;
    }
    if (Object.keys(errors).length > 0) {
      setGeneralError('Please resolve the highlighted errors before continuing.');
      if (errors.full_name) fullNameInputRef.current?.focus();
      else if (errors.mobile_number) mobileInputRef.current?.focus();
      else if (errors.email) emailInputRef.current?.focus();
      return;
    }

    setLoading(true);
    setGeneralError('');
    setServerErrors({});
    submitLock.current = true;

    try {
      const response = await API.post('/applicants/register', {
        fullName: normalizeFullName(formData.full_name) || formData.full_name.trim(),
        mobileNumber:
          extractNationalIndianMobile(formData.mobile_number) || formData.mobile_number.trim(),
        email: normalizeEmail(formData.email) || formData.email.trim(),
      });

      const ref = response.data.applicantReference || 'APP-CREATED';
      setApplicantId(ref);
      setApplicantReference(ref);
      setNeedsIndependentVerificationNotice(false);
      await hydrateVerificationAndProgress(
        { emailVerificationMethod: 'NONE', mobileVerified: false, emailVerified: false },
        2
      );
    } catch (err) {
      const data = err.response?.data;
      if (data?.code === 'VALIDATION_FAILED') {
        const mapped = extractBackendFieldErrors(data);
        if (Object.keys(mapped).length > 0) {
          setServerErrors(mapped);
          setGeneralError('Please resolve the highlighted errors before continuing.');
          if (mapped.full_name) fullNameInputRef.current?.focus();
          else if (mapped.mobile_number) mobileInputRef.current?.focus();
          else if (mapped.email) emailInputRef.current?.focus();
        } else {
          setGeneralError(data?.detail || 'Unable to create registration draft. Please retry.');
        }
      } else if (data?.code === 'EMAIL_ALREADY_REGISTERED') {
        setServerErrors({ email: 'This email is already registered. Log in to continue.' });
        emailInputRef.current?.focus();
      } else if (
        data?.code === 'MOBILE_NUMBER_ALREADY_REGISTERED' ||
        data?.code === 'MOBILE_NUMBER_ALREADY_IN_USE' ||
        data?.code === 'MOBILE_ALREADY_VERIFIED'
      ) {
        setServerErrors({
          mobile_number: 'This mobile number is already registered. Log in to continue.',
        });
        mobileInputRef.current?.focus();
      } else if (data?.code === 'INVALID_MOBILE_NUMBER') {
        setServerErrors({ mobile_number: 'Enter a valid 10-digit mobile number.' });
        mobileInputRef.current?.focus();
      } else if (!err.response) {
        setGeneralError('You appear to be offline. Reconnect to continue — your entries stay on this page.');
      } else {
        setGeneralError(data?.detail || 'Unable to create registration draft. Please retry.');
      }
    } finally {
      setLoading(false);
      submitLock.current = false;
    }
  };

  // ==========================================
  // STEP 2 HANDLERS: CONDITIONAL OTP & CONTACT UPDATE
  // ==========================================
  const handleSwitchChannel = async (nextChannel) => {
    if (nextChannel === selectedChannel || otpBusy !== '') return;

    setOtpDigits(Array(6).fill(''));
    setOtpError('');
    setOtpMessage('');
    setOtpVerifiedBanner(false);
    setActiveChallenge(null);
    setRetryAt(0);
    setEditingContactChannel('');
    setContactEditError('');
    setSelectedChannel(nextChannel);

    if (!online) return;

    setOtpBusy('switch');
    try {
      const response = await API.post('/applicants/me/verification/switch', {
        channel: nextChannel.toUpperCase(),
      });
      setVerificationState(response.data);
      setMobileVerified(response.data.mobile?.verified === true);
      setEmailVerified(response.data.email?.verified === true);
    } catch (err) {
      if (err.response?.status === 401) {
        setApplicantId(null);
        setRestoreNotice(restorationNotice(err, navigator.onLine));
      } else {
        setOtpError(
          err.response?.data?.detail || 'Unable to switch verification method. Please retry.'
        );
      }
    } finally {
      setOtpBusy('');
    }
  };

  const handleSendOtp = async () => {
    if (otpBusy !== '' || !online) return;
    const channel = selectedChannel;
    setOtpBusy('send');
    setOtpError('');
    setOtpMessage('');
    setOtpVerifiedBanner(false);

    try {
      const response = await API.post(`/applicants/me/verification/${channel}/send`, {});
      const dispatch = response.data;
      setNow(Date.now());
      setRetryAt(0);
      setActiveChallenge({
        channel,
        challengeStatus: 'ACTIVE',
        expiresAt: dispatch.expiresAt,
        resendAvailableAt: dispatch.resendAvailableAt,
      });
      setOtpDigits(Array(6).fill(''));

      const statusRes = await API.get('/applicants/me/verification/status');
      setVerificationState(statusRes.data);
      setMobileVerified(statusRes.data.mobile?.verified === true);
      setEmailVerified(statusRes.data.email?.verified === true);

      if (statusRes.data?.[channel]?.challengeStatus === 'DELIVERY_FAILED') {
        setActiveChallenge(null);
        setOtpError('Code delivery failed. Check your contact details and retry after the resend timer.');
        return;
      }

      setOtpMessage(
        (statusRes.data?.deliveryMode || dispatch.deliveryMode) === 'MOCK'
          ? 'Development test code generated. Check backend terminal logs for the 6-digit code.'
          : channel === 'mobile'
            ? 'Verification requested. Check your registered mobile number for a 6-digit code.'
            : 'Verification requested. Check your registered email address for a 6-digit code.'
      );
    } catch (err) {
      const data = err.response?.data;
      const retry = Number(data?.retryAfterSeconds || err.response?.headers?.['retry-after']);
      if (retry > 0) setRetryAt(Date.now() + retry * 1000);
      if (err.response?.status === 401) {
        setApplicantId(null);
        setRestoreNotice(restorationNotice(err, navigator.onLine));
      } else {
        setOtpError(data?.detail || `Failed to send ${channel} verification code. Please retry.`);
      }
    } finally {
      setOtpBusy('');
    }
  };

  const handleVerifyOtp = async () => {
    if (otpBusy !== '' || !online) return;
    const channel = selectedChannel;
    const code = otpDigits.join('');
    if (code.length !== 6 || otpDigits.some((d) => !/^\d$/.test(d))) {
      setOtpError('Please enter the 6-digit verification code.');
      return;
    }

    setOtpBusy('verify');
    setOtpError('');
    setOtpMessage('');

    try {
      await API.post(`/applicants/me/verification/${channel}/verify`, { code });
      const statusRes = await API.get('/applicants/me/verification/status');
      setVerificationState(statusRes.data);
      setMobileVerified(statusRes.data.mobile?.verified === true);
      setEmailVerified(statusRes.data.email?.verified === true);
      if (channel === 'email') {
        setEmailVerificationMethod('EMAIL_OTP');
      }
      setNeedsIndependentVerificationNotice(false);
      setActiveChallenge(null);
      setOtpVerifiedBanner(true);
      setOtpMessage(
        channel === 'mobile'
          ? 'Mobile number verified! Continuing to payment…'
          : 'Email address verified! Continuing to payment…'
      );
      setTimeout(() => {
        goToStep(3, false, 3);
      }, 450);
    } catch (err) {
      const data = err.response?.data;
      const retry = Number(data?.retryAfterSeconds || err.response?.headers?.['retry-after']);
      if (retry > 0) setRetryAt(Date.now() + retry * 1000);
      if (err.response?.status === 401) {
        setApplicantId(null);
        setRestoreNotice(restorationNotice(err, navigator.onLine));
      } else if ([410, 429].includes(err.response?.status)) {
        try {
          const statusRes = await API.get('/applicants/me/verification/status');
          setVerificationState(statusRes.data);
          setActiveChallenge(null);
        } catch {
          // Keep current state
        }
      }
      setOtpError(data?.detail || 'Verification code is invalid or expired. Please retry.');
    } finally {
      setOtpBusy('');
    }
  };

  const handleOpenContactEditor = (channel) => {
    setEditingContactChannel(channel);
    setContactEditValue('');
    setContactEditError('');
    setContactEditTouched(false);
    setOtpError('');
  };

  const handleSaveContactChange = async (e) => {
    e.preventDefault();
    if (otpBusy !== '' || !online) return;

    const val = contactEditValue.trim();
    setContactEditTouched(true);
    if (contactEditError) return;
    if (payloadContactUnchanged(val, editingContactChannel === 'mobile' ? formData.mobile_number : formData.email,
      editingContactChannel)) return;
    let payloadValue = val;
    if (editingContactChannel === 'mobile') {
      const mobileError = validateMobile(val);
      if (mobileError) {
        setContactEditError(mobileError);
        return;
      }
      payloadValue = extractNationalIndianMobile(val) || val;
    } else {
      const emailError = validateRegistrationEmail(val, emailPolicy);
      if (emailError) {
        setContactEditError(emailError);
        return;
      }
      payloadValue = normalizeEmail(val) || val;
    }

    setOtpBusy('change-contact');
    setContactEditError('');
    setOtpError('');

    try {
      if (editingContactChannel === 'mobile') {
        await API.put('/applicants/me/verification/mobile', { mobileNumber: payloadValue });
        setFormData((prev) => ({ ...prev, mobile_number: payloadValue }));
      } else {
        await API.put('/applicants/me/verification/email', { email: payloadValue });
        setFormData((prev) => ({ ...prev, email: payloadValue }));
      }

      const statusRes = await API.get('/applicants/me/verification/status');
      setVerificationState(statusRes.data);
      setActiveChallenge(null);
      setOtpDigits(Array(6).fill(''));
      setEditingContactChannel('');
      setContactEditValue('');
      setOtpMessage(
        editingContactChannel === 'mobile'
          ? 'Mobile number updated. Request a verification code below.'
          : 'Email address updated. Request a verification code below.'
      );
    } catch (err) {
      const data = err.response?.data;
      const fieldMessage = Array.isArray(data?.errors) ? data.errors[0]?.message : '';
      setContactEditError(
        fieldMessage ||
        data?.detail ||
        'Could not update contact right now. Check the value or try again shortly.'
      );
    } finally {
      setOtpBusy('');
    }
  };

  const backendChannelState = verificationState?.[selectedChannel];
  const mergedChannelState =
    activeChallenge && activeChallenge.channel === selectedChannel
      ? { ...backendChannelState, ...activeChallenge }
      : backendChannelState;

  const isSelectedVerified = selectedChannel === 'mobile' ? mobileVerified : emailVerified;
  const resendAt = Date.parse(mergedChannelState?.resendAvailableAt) || 0;
  const cooldown = Math.max(0, Math.ceil((Math.max(resendAt, retryAt) - now) / 1000));
  const expiresAt = Date.parse(mergedChannelState?.expiresAt) || 0;
  const isExpired =
    mergedChannelState?.challengeStatus === 'EXPIRED' || (expiresAt > 0 && now >= expiresAt);
  const isLocked = mergedChannelState?.challengeStatus === 'LOCKED';
  const hasChallenge =
    isLocked || (expiresAt > 0 && ['ACTIVE', 'EXPIRED'].includes(mergedChannelState?.challengeStatus));
  const expiresInSeconds = expiresAt > 0 ? Math.max(0, Math.ceil((expiresAt - now) / 1000)) : 0;
  const channelEnabled = mergedChannelState?.enabled !== false;

  const maskedMobileDest =
    verificationState?.mobile?.maskedDestination || maskMobile(formData.mobile_number) || '';
  const maskedEmailDest =
    verificationState?.email?.maskedDestination || maskEmail(formData.email) || '';
  const selectedMaskedDestination =
    selectedChannel === 'mobile'
      ? maskedMobileDest || 'Registered Mobile'
      : maskedEmailDest || 'Registered Email';

  const linkResendCooldown = Math.max(0, Math.ceil((linkRetryAt - now) / 1000));

  // ==========================================
  // DYNAMIC HEADINGS & SUBTITLES PER SCREEN
  // ==========================================
  let pageTitle = 'Create your account';
  let pageSubtitle = 'Start your affiliate journey with ZayloPe.';

  if (currentStep === 1) {
    if (googleLinking && !applicantId) {
      pageTitle = 'Confirm your existing account';
      pageSubtitle = 'Verify ownership before connecting Google.';
    } else if (googleOnboarding && !applicantId) {
      pageTitle = 'Complete your profile';
      pageSubtitle = 'Just a few details to get started.';
    }
  } else if (currentStep === 2) {
    if (isSelectedVerified || !hasChallenge) {
      pageTitle = 'Verify your contact details';
      pageSubtitle = 'Choose where you would like to receive your verification code.';
    } else if (selectedChannel === 'mobile') {
      pageTitle = 'Verify your mobile number';
      pageSubtitle = `Enter the 6-digit code sent to ${selectedMaskedDestination}.`;
    } else {
      pageTitle = 'Verify your email address';
      pageSubtitle = `Enter the 6-digit code sent to ${selectedMaskedDestination}.`;
    }
  } else if (currentStep === 3) {
    pageTitle = 'Activate your membership';
    pageSubtitle = 'One-time ₹599 payment via Razorpay for lifetime membership and your permanent Affiliate ID.';
  } else if (currentStep === 4) {
    pageTitle = null;
    pageSubtitle = null;
  }

  const handleBeforeBackToHome = () => {
    if (paymentBusy) {
      setGeneralError(
        'Your payment confirmation is still in progress. Please wait a moment before leaving this page.'
      );
      return false;
    }
    return true;
  };

  return (
    <RegistrationLayout
      currentStep={currentStep}
      maxAllowedStep={maxAllowedStep}
      steps={STEP_LABELS}
      compactSteps={COMPACT_STEP_LABELS}
      onStepClick={goToStep}
      title={pageTitle}
      subtitle={pageSubtitle}
      restoreNotice={restoreNotice}
      onRetryRestore={restoreDraft}
      restoring={restoring}
      showStepper={true}
      onBeforeBackToHome={handleBeforeBackToHome}
    >
      {!online && currentStep !== 2 && (
        <StatusMessage kind="info">
          You appear to be offline. Reconnect to continue — your entries stay on this page.
        </StatusMessage>
      )}
      {generalError && (
        <FormError error={generalError} onDismiss={() => setGeneralError('')} />
      )}
      {(currentStep === 1 || editingContactChannel === 'email') && !emailPolicy && (
        emailPolicyError ? <StatusMessage kind="error">
          {emailPolicyError}{' '}
          <button type="button" onClick={() => { setEmailPolicyError(''); setEmailPolicyAttempt(value => value + 1); }}>
            Retry email validation
          </button>
        </StatusMessage> : <p role="status">Loading email validation policy…</p>
      )}

      {/* =======================================================
          STEP 1: ACCOUNT (MANUAL / GOOGLE PROFILE COMPLETION / LINKING / DRAFT)
         ======================================================= */}
      {currentStep === 1 &&
        (applicantId ? (
          <div className="zp-account-form zp-form-column">
            <div className="zp-draft-summary-card">
              <div className="zp-draft-summary-row">
                <span className="zp-draft-summary-label">Full name</span>
                <span className="zp-draft-summary-value">{formData.full_name || '—'}</span>
              </div>
              <div className="zp-draft-summary-row">
                <span className="zp-draft-summary-label">Mobile number</span>
                <span className="zp-draft-summary-value">
                  {maskedMobileDest || '—'}
                </span>
              </div>
              <div className="zp-draft-summary-row">
                <span className="zp-draft-summary-label">Email address</span>
                <span className="zp-draft-summary-value">
                  {maskedEmailDest || '—'}
                </span>
              </div>
            </div>
            <div className="zp-step-actions">
              <LoadingButton
                type="button"
                onClick={() => goToStep(contactVerified ? 3 : 2)}
              >
                {contactVerified ? 'Continue to Payment' : 'Continue to Verification'}
              </LoadingButton>
            </div>
          </div>
        ) : googleLinking ? (
          <div className="zp-linking-panel zp-form-column-wide">
            <GoogleAccountCard
              email={googleLinking.email}
              pictureUrl={googleLinking.pictureUrl}
              badgeMode="linking"
              onUseDifferentAccount={handleUseDifferentGoogleAccount}
              disabled={linkBusy}
            />

            <div className="zp-linking-summary-box">
              <p>
                An existing ZayloPe account matches this email. Confirm ownership with a 6-digit
                verification code sent to{' '}
                <strong>{googleLinking.maskedTarget || 'your verified contact'}</strong>.
              </p>
            </div>

            {linkOtpMessage && <StatusMessage kind="success">{linkOtpMessage}</StatusMessage>}
            {linkOtpError && (
              <FormError error={linkOtpError} onDismiss={() => setLinkOtpError('')} />
            )}

            {!linkChallengeSent ? (
              <div className="zp-step-actions">
                <LoadingButton
                  type="button"
                  loading={linkBusy}
                  disabled={!online}
                  onClick={handleSendLinkingOtp}
                >
                  Send verification code
                </LoadingButton>
              </div>
            ) : (
              <form onSubmit={handleVerifyLinkingOtp} className="zp-linking-panel" noValidate>
                <div className="zp-linking-otp-center">
                  <OtpInput
                    channel={googleLinking.linkingChannel === 'EMAIL' ? 'Email' : 'Mobile'}
                    value={linkOtpDigits}
                    onChange={setLinkOtpDigits}
                    disabled={linkBusy || !online}
                    hasError={Boolean(linkOtpError)}
                  />
                </div>
                <div className="zp-channel-btn-row">
                  <LoadingButton
                    type="submit"
                    loading={linkBusy}
                    disabled={!online || linkOtpDigits.some((d) => !/^\d$/.test(d))}
                    className="zp-verify-btn"
                  >
                    Confirm &amp; Continue
                  </LoadingButton>
                  <SecondaryButton
                    disabled={linkResendCooldown > 0 || linkBusy || !online}
                    onClick={handleSendLinkingOtp}
                  >
                    {linkResendCooldown > 0 ? `Resend in ${linkResendCooldown}s` : 'Resend code'}
                  </SecondaryButton>
                </div>
              </form>
            )}
          </div>
        ) : googleOnboarding ? (
          <div className="zp-account-form zp-form-column-wide">
            <GoogleAccountCard
              fullName={googleOnboarding.fullName}
              email={googleOnboarding.email}
              pictureUrl={googleOnboarding.pictureUrl}
              authoritativeEmail={googleOnboarding.authoritativeEmail}
              badgeMode="completion"
              onUseDifferentAccount={handleUseDifferentGoogleAccount}
              disabled={loading}
            />

            {emailPolicy && (serverErrors.email || validateRegistrationEmail(googleOnboarding.email, emailPolicy)) && (
              <FormError error={serverErrors.email || validateRegistrationEmail(googleOnboarding.email, emailPolicy)} />
            )}

            <form onSubmit={handleGoogleProfileSubmit} noValidate>
              <FormInput
                id="google-full-name"
                label="Full name"
                name="full_name"
                leadingIcon={<User size={18} strokeWidth={1.9} />}
                inputRef={googleNameInputRef}
                value={formData.full_name}
                onChange={handlePersonalChange}
                onBlur={() => handleFieldBlur('full_name')}
                placeholder="Enter your full legal name"
                autoComplete="name"
                required
                disabled={loading}
                valid={isFieldFormatValid('full_name')}
                error={getFieldError('full_name')}
              />

              <PhoneInput
                id="google-mobile-number"
                label="Mobile number"
                name="mobile_number"
                leadingIcon={<Smartphone size={18} strokeWidth={1.9} />}
                inputRef={googleMobileInputRef}
                value={formData.mobile_number}
                onChange={handlePersonalChange}
                onRejectedInput={(message) =>
                  setInputRestrictionErrors((prev) => ({ ...prev, mobile_number: message || undefined }))
                }
                onBlur={() => handleFieldBlur('mobile_number')}
                placeholder="10-digit mobile number"
                required
                disabled={loading}
                valid={isFieldFormatValid('mobile_number')}
                error={getFieldError('mobile_number')}
                helperText="For important updates about your account."
              />

              <MembershipSummary />

              <div className="zp-step-actions">
                <LoadingButton loading={loading} disabled={!online || !emailPolicy || Object.values(liveFieldErrors).some(Boolean)}>
                  Continue
                </LoadingButton>
              </div>
            </form>
          </div>
        ) : (
          <div className="zp-account-form zp-form-column">
            {googleNotice && <StatusMessage kind="info">{googleNotice}</StatusMessage>}

            <GoogleAuthButton
              onSuccess={handleGoogleSuccess}
              onError={handleGoogleError}
              loading={googleLoading}
              text="Continue with Google"
              disabled={loading}
            />

            <AuthDivider text="or register manually" />

            <form onSubmit={handlePersonalSubmit} noValidate>
              <FormInput
                id="full-name"
                label="Full name"
                name="full_name"
                leadingIcon={<User size={18} strokeWidth={1.9} />}
                inputRef={fullNameInputRef}
                value={formData.full_name}
                onChange={handlePersonalChange}
                onBlur={() => handleFieldBlur('full_name')}
                placeholder="Enter your full legal name"
                autoComplete="name"
                required
                disabled={loading || googleLoading}
                valid={isFieldFormatValid('full_name')}
                error={getFieldError('full_name')}
              />

              <PhoneInput
                id="mobile-number"
                label="Mobile number"
                name="mobile_number"
                leadingIcon={<Smartphone size={18} strokeWidth={1.9} />}
                inputRef={mobileInputRef}
                value={formData.mobile_number}
                onChange={handlePersonalChange}
                onRejectedInput={(message) =>
                  setInputRestrictionErrors((prev) => ({ ...prev, mobile_number: message || undefined }))
                }
                onBlur={() => handleFieldBlur('mobile_number')}
                placeholder="10-digit mobile number"
                required
                disabled={loading || googleLoading}
                valid={isFieldFormatValid('mobile_number')}
                error={getFieldError('mobile_number')}
              />

              <FormInput
                id="email-address"
                label="Email address"
                name="email"
                type="email"
                inputMode="email"
                leadingIcon={<Mail size={18} strokeWidth={1.9} />}
                inputRef={emailInputRef}
                value={formData.email}
                onChange={handlePersonalChange}
                onBlur={() => handleFieldBlur('email')}
                placeholder="you@example.com"
                maxLength={254}
                autoComplete="email"
                required
                disabled={loading || googleLoading}
                valid={isFieldFormatValid('email')}
                error={getFieldError('email')}
                suggestion={emailSuggestion}
                helperText={loading
                  ? 'Checking your details and email domain…'
                  : emailPolicy && !validateRegistrationEmail(formData.email, emailPolicy)
                    ? 'Email provider accepted. Ownership has not been verified.'
                    : 'Use a supported email provider. Email ownership is checked separately.'}
                onApplySuggestion={(suggestion) => {
                  setFormData((prev) => ({ ...prev, email: suggestion }));
                  setServerErrors((prev) => ({ ...prev, email: undefined }));
                  setGeneralError('');
                }}
              />

              <MembershipSummary />

              <div className="zp-step-actions">
                <LoadingButton loading={loading} disabled={!online || !emailPolicy || Object.values(liveFieldErrors).some(Boolean)}>
                  Continue
                </LoadingButton>
              </div>
            </form>

            <p className="zp-account-login-prompt">
              Already have an account?{' '}
              <Link to="/login" className="zp-account-login-link">
                Log in
              </Link>
            </p>
          </div>
        ))}

      {/* =======================================================
          STEP 2: VERIFICATION SELECTION + MOBILE / EMAIL OTP
         ======================================================= */}
      {currentStep === 2 && (
        <div className="zp-verification-container">
          {!online && (
            <StatusMessage kind="info">
              You are offline. Reconnect to send or verify a verification code.
            </StatusMessage>
          )}

          {needsIndependentVerificationNotice && (
            <StatusMessage kind="info">
              Your email address could not be confirmed automatically. Verify your mobile number or
              email below to continue.
            </StatusMessage>
          )}

          {import.meta.env.DEV && verificationState?.deliveryMode === 'MOCK' && (
            <StatusMessage kind="info">
              Local development mode: test codes are logged to backend terminal logs.
            </StatusMessage>
          )}

          <VerificationMethodSelector
            selectedChannel={selectedChannel}
            onSelect={handleSwitchChannel}
            mobileVerified={mobileVerified}
            emailVerified={emailVerified}
            emailVerificationMethod={emailVerificationMethod}
            maskedMobile={maskedMobileDest}
            maskedEmail={maskedEmailDest}
            disabled={otpBusy !== ''}
          />

          {otpMessage && <StatusMessage kind="success">{otpMessage}</StatusMessage>}
          {otpError && <FormError error={otpError} onDismiss={() => setOtpError('')} />}

          <div className="zp-channel-box">
            {isSelectedVerified ? (
              <ChannelVerifiedCard
                title={selectedChannel === 'mobile' ? 'Mobile verification' : 'Email verification'}
                destination={selectedMaskedDestination}
                badgeLabel={
                  selectedChannel === 'email' && emailVerificationMethod === 'GOOGLE'
                    ? 'Email verified with Google'
                    : 'Verified'
                }
              />
            ) : (
              <section
                className="zp-active-channel-card"
                aria-label={selectedChannel === 'mobile' ? 'Mobile OTP Verification' : 'Email OTP Verification'}
              >
                <div className="zp-channel-header">
                  <h2 className="zp-channel-title">
                    {selectedChannel === 'mobile' ? 'Mobile verification' : 'Email verification'}
                  </h2>
                  <span className="zp-required-pill">Selected</span>
                </div>

                <div className="zp-channel-destination-row">
                  <p className="zp-channel-destination" data-testid="selected-channel-destination">
                    {selectedMaskedDestination}
                  </p>
                  {editingContactChannel !== selectedChannel && (
                    <button
                      type="button"
                      className="zp-change-contact-btn"
                      disabled={otpBusy !== ''}
                      onClick={() => handleOpenContactEditor(selectedChannel)}
                    >
                      {selectedChannel === 'mobile' ? 'Change mobile number' : 'Change email address'}
                    </button>
                  )}
                </div>

                {editingContactChannel === selectedChannel && (
                  <form className="zp-change-contact-form" onSubmit={handleSaveContactChange} noValidate>
                    {selectedChannel === 'mobile' ? (
                      <PhoneInput
                        id="edit-mobile-number"
                        label="New mobile number"
                        name="new_mobile"
                        leadingIcon={<Smartphone size={18} />}
                        value={contactEditValue}
                        onChange={(ev) => {
                          setContactEditValue(ev.target.value);
                          setContactEditError('');
                        }}
                        onRejectedInput={setContactEditError}
                        onBlur={() => setContactEditTouched(true)}
                        placeholder="10-digit mobile number"
                        required
                        disabled={otpBusy !== ''}
                        valid={
                          Boolean(contactEditValue.trim()) &&
                          !contactEditError &&
                          !validateMobile(contactEditValue)
                        }
                        error={contactEditError || (contactEditTouched ? validateMobile(contactEditValue) : '')}
                      />
                    ) : (
                      <FormInput
                        id="edit-email-address"
                        maxLength={254}
                        label="New email address"
                        name="new_email"
                        type="email"
                        inputMode="email"
                        leadingIcon={<Mail size={18} />}
                        value={contactEditValue}
                        onChange={(ev) => {
                          setContactEditValue(ev.target.value);
                          setContactEditError('');
                        }}
                        placeholder="you@example.com"
                        onBlur={() => setContactEditTouched(true)}
                        required
                        disabled={otpBusy !== ''}
                        valid={
                          Boolean(contactEditValue.trim()) &&
                          !contactEditError &&
                          otpBusy === '' &&
                          !validateRegistrationEmail(contactEditValue, emailPolicy)
                        }
                        error={contactEditError || (contactEditTouched ? validateRegistrationEmail(contactEditValue, emailPolicy) : '')}
                        suggestion={
                          contactEditTouched ? getEmailDomainSuggestion(contactEditValue) : null
                        }
                        onApplySuggestion={(suggestion) => {
                          setContactEditValue(suggestion);
                          setContactEditError('');
                        }}
                      />
                    )}
                    <div className="zp-change-contact-actions">
                      <SecondaryButton
                        disabled={otpBusy !== ''}
                        onClick={() => {
                          setEditingContactChannel('');
                          setContactEditError('');
                        }}
                      >
                        Cancel
                      </SecondaryButton>
                      <LoadingButton loading={otpBusy === 'change-contact'} disabled={!online || otpBusy !== '' || !contactEditValue.trim() || Boolean(contactEditError) || Boolean(selectedChannel === 'mobile'
                        ? validateMobile(contactEditValue)
                        : validateRegistrationEmail(contactEditValue, emailPolicy)) || (selectedChannel === 'mobile'
                        ? payloadContactUnchanged(contactEditValue, formData.mobile_number, 'mobile')
                        : payloadContactUnchanged(contactEditValue, formData.email, 'email'))}>
                        Save {selectedChannel === 'mobile' ? 'number' : 'email'}
                      </LoadingButton>
                    </div>
                  </form>
                )}

                {!channelEnabled && (
                  <p className="zp-channel-unconfigured" role="note">
                    {selectedChannel === 'mobile'
                      ? 'SMS code delivery is not configured right now. You may switch to Email address.'
                      : 'Email code delivery is not configured right now. You may switch to Mobile number.'}
                  </p>
                )}

                {hasChallenge && !isLocked && (
                  <div className="zp-otp-entry-section">
                    <p className="zp-otp-instructions">
                      {selectedChannel === 'mobile'
                        ? 'Enter the 6-digit code sent to your mobile number.'
                        : 'Enter the 6-digit code sent to your email address.'}
                    </p>
                    <OtpInput
                      channel={selectedChannel === 'mobile' ? 'Mobile' : 'Email'}
                      value={otpDigits}
                      onChange={(nextDigits) => {
                        setOtpDigits(nextDigits);
                        if (otpError) setOtpError('');
                      }}
                      disabled={otpBusy !== '' || isExpired || !online}
                      hasError={Boolean(otpError)}
                      isSuccess={otpVerifiedBanner}
                    />
                    {isExpired ? (
                      <p className="zp-otp-expired-msg" role="status">
                        Code expired. Please request a new code.
                      </p>
                    ) : expiresInSeconds > 0 ? (
                      <p className="zp-otp-timer-msg">Code expires in {expiresInSeconds}s</p>
                    ) : null}
                  </div>
                )}

                {isLocked && (
                  <p className="zp-otp-expired-msg" role="status">
                    Too many incorrect attempts. Request a new code when the resend timer finishes.
                  </p>
                )}

                <div className="zp-channel-btn-row">
                  {hasChallenge ? (
                    <>
                      <LoadingButton
                        type="button"
                        loading={otpBusy === 'verify'}
                        disabled={
                          isExpired ||
                          isLocked ||
                          !online ||
                          otpBusy !== '' ||
                          otpDigits.some((d) => !/^\d$/.test(d))
                        }
                        onClick={handleVerifyOtp}
                        className="zp-verify-btn"
                      >
                        Verify and continue
                      </LoadingButton>
                      <SecondaryButton
                        disabled={cooldown > 0 || otpBusy !== '' || !online || !channelEnabled}
                        onClick={handleSendOtp}
                      >
                        {cooldown > 0 ? `Resend in ${cooldown}s` : 'Resend code'}
                      </SecondaryButton>
                    </>
                  ) : (
                    <LoadingButton
                      type="button"
                      loading={otpBusy === 'send'}
                      disabled={cooldown > 0 || otpBusy !== '' || !online || !channelEnabled}
                      onClick={handleSendOtp}
                    >
                      {cooldown > 0
                        ? `Send in ${cooldown}s`
                        : selectedChannel === 'mobile'
                          ? 'Send SMS code'
                          : 'Send email code'}
                    </LoadingButton>
                  )}
                </div>
              </section>
            )}
          </div>

          <p className="zp-verification-footnote">
            Complete either Mobile or Email verification to continue.
          </p>

          <div className="zp-step-actions zp-dual-actions">
            <SecondaryButton onClick={() => goToStep(1)} disabled={otpBusy !== ''}>
              Back
            </SecondaryButton>
            <LoadingButton
              type="button"
              disabled={!contactVerified || otpBusy !== ''}
              onClick={() => goToStep(3)}
            >
              Continue to Payment
            </LoadingButton>
          </div>
        </div>
      )}

      {/* =======================================================
          STEP 3: MEMBERSHIP PAYMENT & CONSENT
         ======================================================= */}
      {currentStep === 3 && (
        <PaymentCheckout
          applicantName={formData.full_name}
          applicantEmail={formData.email}
          applicantMobile={formData.mobile_number}
          mobileVerified={mobileVerified}
          emailVerified={emailVerified}
          emailVerificationMethod={emailVerificationMethod}
          maskedMobile={maskedMobileDest}
          maskedEmail={maskedEmailDest}
          registrationSubmitted={registrationSubmitted}
          backendConsentAccepted={backendConsentAccepted}
          onConsentSubmitted={() => {
            setRegistrationSubmitted(true);
            setBackendConsentAccepted(true);
          }}
          onNameUpdated={(nextName) => {
            setFormData((prev) => ({ ...prev, full_name: nextName }));
          }}
          onEditContact={(channel) => {
            setSelectedChannel(channel || 'mobile');
            goToStep(2);
          }}
          onBusyChange={setPaymentBusy}
          onPaymentSuccess={(paymentResult) => {
            setPaymentBusy(false);
            if (paymentResult?.affiliateId) {
              setOnboardingStatus(paymentResult);
            }
            setHasPendingOrder(false);
            setPaymentCompleted(true);
            goToStep(4, true, 4);
          }}
          onBack={!registrationSubmitted ? () => goToStep(2) : undefined}
        />
      )}

      {/* =======================================================
          STEP 4: MEMBERSHIP SUCCESS & AFFILIATE ID / STATUS
         ======================================================= */}
      {currentStep === 4 && (
        <ApprovalStatus
          applicantName={formData.full_name}
          initialStatus={onboardingStatus}
        />
      )}
    </RegistrationLayout>
  );
}


