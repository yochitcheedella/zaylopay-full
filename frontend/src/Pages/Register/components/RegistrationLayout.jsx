import React from 'react';
import { Link } from 'react-router-dom';
import AuthLayout from '../../../Components/Auth/AuthLayout';
import RegistrationStepper from './RegistrationStepper';
import './RegistrationLayout.css';

const STEP_TO_CARD_SIZE = {
  1: 'register', // 640px
  2: 'compact',  // 560px
  3: 'payment',  // 680px
  4: 'status',   // 640px
};

export default function RegistrationLayout({
  currentStep = 1,
  maxAllowedStep = currentStep,
  steps = [],
  compactSteps = steps,
  onStepClick,
  title,
  subtitle,
  children,
  restoreNotice,
  onRetryRestore,
  restoring = false,
  showStepper = true,
  cardClassName = '',
  contentClassName = '',
  cardSize,
  onBeforeBackToHome,
}) {
  const stepperNode = showStepper ? (
    <RegistrationStepper
      steps={steps}
      compactSteps={compactSteps}
      currentStep={currentStep}
      maxAllowedStep={maxAllowedStep}
      onStepClick={onStepClick}
      disabled={restoring}
    />
  ) : null;

  const statusPrefix = (
    <>
      {restoreNotice && (
        <div
          className={`zp-card-notice zp-card-notice-${restoreNotice.kind}`}
          role={restoreNotice.kind === 'error' ? 'alert' : 'status'}
        >
          <div className="zp-card-notice-body">
            <p>{restoreNotice.message}</p>
            {restoreNotice.retry && (
              <button
                type="button"
                disabled={restoring}
                onClick={onRetryRestore}
                className="zp-notice-retry-btn"
              >
                {restoring ? 'Checking…' : 'Retry connection'}
              </button>
            )}
            {restoreNotice.kind === 'session' && (
              <Link to="/login" className="zp-notice-login-link">
                Log in
              </Link>
            )}
          </div>
        </div>
      )}

      {restoring && (
        <div className="zp-restoring-status" role="status">
          <span className="zp-restoring-spinner" aria-hidden="true" />
          <span>Checking for a saved application…</span>
        </div>
      )}
    </>
  );

  return (
    <AuthLayout
      cardSize={cardSize || STEP_TO_CARD_SIZE[currentStep] || 'register'}
      title={title}
      subtitle={subtitle}
      cardHeader={stepperNode}
      cardPrefix={statusPrefix}
      cardClassName={cardClassName}
      contentClassName={contentClassName}
      onBeforeBackToHome={onBeforeBackToHome}
    >
      {children}
    </AuthLayout>
  );
}
