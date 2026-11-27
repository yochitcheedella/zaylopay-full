import React from 'react';
import BrandLogo from '../PublicLayout/BrandLogo';
import {
  FormInput,
  LoadingButton,
  SecondaryButton,
  FormError,
  StatusMessage,
} from '../../Pages/Register/components/FormControls';
import RegistrationStepper from '../../Pages/Register/components/RegistrationStepper';
import './AuthLayout.css';
export { default as AuthOtp } from '../../Pages/Register/components/OtpInput';

/**
 * Unified Expanded Authentication Card Container.
 * Renders content in strict hierarchy: BrandLogo -> Stepper -> Heading -> Subtitle -> Body.
 */
export function AuthCard({
  title,
  subtitle,
  children,
  className = '',
  headerPrefix,
  showBrandLogo = true,
}) {
  return (
    <div className={`zp-auth-card ${className}`.trim()}>
      {(showBrandLogo || title || subtitle || headerPrefix) && (
        <div className="zp-auth-card-heading">
          {showBrandLogo && (
            <div className="zp-auth-card-logo">
              <BrandLogo />
            </div>
          )}
          {headerPrefix}
          <AuthHeading title={title} subtitle={subtitle} />
        </div>
      )}
      <div className="zp-auth-card-body">{children}</div>
    </div>
  );
}

/**
 * Shared Input alias
 */
export const AuthInput = FormInput;

/**
 * Shared Button alias to Primary / Secondary actions
 */
export function AuthButton({
  children,
  variant = 'primary',
  loading = false,
  disabled = false,
  type = 'submit',
  onClick,
  className = '',
  ...props
}) {
  if (variant === 'secondary') {
    return (
      <SecondaryButton
        type={type}
        disabled={disabled || loading}
        onClick={onClick}
        className={className}
        {...props}
      >
        {children}
      </SecondaryButton>
    );
  }

  return (
    <LoadingButton
      type={type}
      loading={loading}
      disabled={disabled}
      onClick={onClick}
      className={className}
      {...props}
    >
      {children}
    </LoadingButton>
  );
}

/**
 * Shared Error Banner
 */
export function AuthError({ error, title, id, onDismiss, className = '' }) {
  if (!error) return null;
  return (
    <FormError
      id={id}
      error={error}
      title={title}
      onDismiss={onDismiss}
      className={className}
    />
  );
}

/**
 * Shared Loading State
 */
export function LoadingState({ message = 'Loading…', className = '' }) {
  return (
    <div className={`zp-loading-state ${className}`.trim()} role="status">
      <span className="zp-loading-spinner" aria-hidden="true" />
      <span>{message}</span>
    </div>
  );
}

export { RegistrationStepper, StatusMessage };

/** Shared finite ownership-confirmed success mark. */
export function AuthSuccess({ animate = true }) {
  return <div className={`zp-auth-success zp-welcome-check-ring ${animate ? 'is-animated' : ''}`} aria-hidden="true"><svg width="28" height="28" viewBox="0 0 24 24" fill="none"><path d="m5 12 4 4L19 6" stroke="currentColor" strokeWidth="2.75" strokeLinecap="round" strokeLinejoin="round" /></svg></div>;
}

export function AuthHeading({title, subtitle}) { return <>{title && <h1 className="zp-auth-card-title">{title}</h1>}{subtitle && <p className="zp-auth-card-subtitle">{subtitle}</p>}</>; }

