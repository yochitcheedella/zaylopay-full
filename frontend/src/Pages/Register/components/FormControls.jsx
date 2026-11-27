import React, { useEffect, useRef, useState } from 'react';
import { Pencil, Check, X } from 'lucide-react';
import {
  MOBILE_MESSAGE,
  normalizePhoneInputValue,
} from '../../../utils/validation';
import './FormControls.css';

/**
 * Reusable accessible input with reference-inspired lightweight CSS validation animation:
 * - Focus: 180ms emerald border + focus ring
 * - Editing: calm pencil indicator during active keystrokes (stops quickly when paused)
 * - Valid format: 350ms left-to-right green sweep + soft green surface (#E6FBE0) + checkmark badge
 * - Invalid format: soft pink surface (#FFF0F1) + red cross badge + inline alert text
 */
export function FormInput({
  id,
  label,
  name,
  type = 'text',
  value,
  onChange,
  onBlur,
  onFocus,
  error,
  valid = false,
  prefix,
  leadingIcon,
  suffix,
  placeholder,
  maxLength,
  autoComplete,
  required = false,
  disabled = false,
  helperText,
  suggestion,
  onApplySuggestion,
  inputMode,
  inputRef,
  ...props
}) {
  const [focused, setFocused] = useState(false);
  const [editing, setEditing] = useState(false);
  const editTimerRef = useRef(null);

  useEffect(() => {
    return () => {
      if (editTimerRef.current) clearTimeout(editTimerRef.current);
    };
  }, []);

  const errorId = error ? `${id}-error` : undefined;
  const helperId = helperText ? `${id}-helper` : undefined;
  const suggestionId = suggestion ? `${id}-suggestion` : undefined;
  const describedBy = [errorId, helperId, suggestionId].filter(Boolean).join(' ') || undefined;

  const hasValue = Boolean(value);
  const isInvalid = Boolean(error);
  const isEditingNow = hasValue && editing;
  const isValid = Boolean(valid) && hasValue && !isInvalid && !isEditingNow;
  const showPencil = focused && isEditingNow && !isInvalid && !disabled;

  const handleFocus = (e) => {
    setFocused(true);
    onFocus?.(e);
  };

  const handleBlur = (e) => {
    setFocused(false);
    setEditing(false);
    if (editTimerRef.current) clearTimeout(editTimerRef.current);
    onBlur?.(e);
  };

  const handleChange = (e) => {
    setEditing(true);
    if (editTimerRef.current) clearTimeout(editTimerRef.current);
    editTimerRef.current = setTimeout(() => {
      setEditing(false);
    }, 380);
    onChange?.(e);
  };

  const fieldStateClass = [
    'zp-form-field',
    focused ? 'is-focused' : '',
    showPencil ? 'is-editing' : '',
    isValid ? 'is-valid' : '',
    isInvalid ? 'has-error' : '',
    disabled ? 'is-disabled' : '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={fieldStateClass}>
      <label htmlFor={id} className="zp-field-label">
        <span>{label}</span>
        {required && <span className="zp-required-star" aria-hidden="true">*</span>}
      </label>

      <div
        className={`zp-input-wrapper ${leadingIcon ? 'has-leading-icon' : ''} ${prefix ? 'has-prefix' : ''
          } ${suffix ? 'has-suffix' : ''}`}
      >
        {/* Background sweep animation layer (always behind input text at z-index 0) */}
        <span className="zp-input-valid-fill" aria-hidden="true" />
        <span className="zp-input-sweep" aria-hidden="true" />

        {leadingIcon && (
          <span className="zp-input-leading-icon" aria-hidden="true">
            {leadingIcon}
          </span>
        )}

        {prefix && (
          <span className="zp-input-prefix" aria-hidden="true">
            {prefix}
          </span>
        )}

        <input
          ref={inputRef}
          id={id}
          name={name}
          type={type}
          value={value}
          onChange={handleChange}
          onFocus={handleFocus}
          onBlur={handleBlur}
          placeholder={placeholder}
          maxLength={maxLength}
          autoComplete={autoComplete}
          required={required}
          disabled={disabled}
          inputMode={inputMode}
          aria-invalid={isInvalid}
          aria-describedby={describedBy}
          className="zp-field-input"
          {...props}
        />

        {suffix && <span className="zp-input-suffix">{suffix}</span>}

        {/* Fixed-width status indicator slot to prevent any horizontal layout shift */}
        <span className="zp-input-status-slot" aria-hidden="true">
          <span className={`zp-status-dot zp-dot-pencil ${showPencil ? 'is-visible' : ''}`}>
            <Pencil size={14} strokeWidth={2.2} />
          </span>
          <span className={`zp-status-dot zp-dot-valid ${isValid ? 'is-visible' : ''}`}>
            <Check size={13} strokeWidth={2.75} />
          </span>
          <span className={`zp-status-dot zp-dot-error ${isInvalid ? 'is-visible' : ''}`}>
            <X size={13} strokeWidth={2.75} />
          </span>
        </span>
      </div>

      {isInvalid ? (
        <p className="zp-field-error" id={errorId} role="alert">
          <svg className="zp-error-icon" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
            <path
              fillRule="evenodd"
              d="M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14zm-.75-9.75a.75.75 0 0 1 1.5 0v4a.75.75 0 0 1-1.5 0v-4zm.75 6.75a1 1 0 1 0 0-2 1 1 0 0 0 0 2z"
            />
          </svg>
          <span>{error}</span>
        </p>
      ) : helperText ? (
        <p className="zp-field-helper" id={helperId}>
          {helperText}
        </p>
      ) : null}
      {suggestion && (
        <p className="zp-email-suggestion" id={suggestionId}>
          <span>Did you mean {suggestion}?</span>
          <button type="button" onClick={() => onApplySuggestion?.(suggestion)}>
            Use suggested address
          </button>
        </p>
      )}
    </div>
  );
}

export const AnimatedInput = FormInput;

export function PhoneInput({
  value = '',
  onChange,
  onRejectedInput,
  error,
  name,
  leadingIcon,
  ...props
}) {
  const [rejectedValue, setRejectedValue] = useState(null);
  const inputError = rejectedValue === value ? MOBILE_MESSAGE : '';

  const handlePhoneChange = (e) => {
    const nextValue = normalizePhoneInputValue(e.target.value);
    if (nextValue === null) {
      setRejectedValue(value);
      onRejectedInput?.(MOBILE_MESSAGE);
      return;
    }
    setRejectedValue(null);
    onRejectedInput?.('');
    onChange?.({
      target: {
        name: name || 'mobile_number',
        value: nextValue,
      },
    });
  };

  return (
    <FormInput
      type="tel"
      inputMode="numeric"
      prefix="+91"
      leadingIcon={leadingIcon}
      autoComplete="tel-national"
      name={name}
      value={value}
      onChange={handlePhoneChange}
      error={inputError || error}
      {...props}
      maxLength={undefined}
    />
  );
}

export function FormSelect({
  id,
  label,
  name,
  value,
  onChange,
  options = [],
  error,
  disabled = false,
  required = false,
  helperText,
  ...props
}) {
  const errorId = error ? `${id}-error` : undefined;
  const helperId = helperText ? `${id}-helper` : undefined;
  const describedBy = [errorId, helperId].filter(Boolean).join(' ') || undefined;

  return (
    <div className={`zp-form-field ${error ? 'has-error' : ''} ${disabled ? 'is-disabled' : ''}`}>
      <label htmlFor={id} className="zp-field-label">
        <span>{label}</span>
        {required && <span className="zp-required-star" aria-hidden="true">*</span>}
      </label>

      <div className="zp-select-wrapper">
        <select
          id={id}
          name={name}
          value={value}
          onChange={onChange}
          disabled={disabled}
          required={required}
          aria-invalid={Boolean(error)}
          aria-describedby={describedBy}
          className="zp-field-select"
          {...props}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value} disabled={opt.disabled}>
              {opt.label}
            </option>
          ))}
        </select>
        <span className="zp-select-arrow" aria-hidden="true">
          <svg viewBox="0 0 20 20" fill="currentColor">
            <path
              fillRule="evenodd"
              d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
              clipRule="evenodd"
            />
          </svg>
        </span>
      </div>

      {error ? (
        <p className="zp-field-error" id={errorId} role="alert">
          <span>{error}</span>
        </p>
      ) : helperText ? (
        <p className="zp-field-helper" id={helperId}>
          {helperText}
        </p>
      ) : null}
    </div>
  );
}

export function FormError({ title, error, id, onDismiss, className = '' }) {
  if (!error) return null;
  return (
    <div className={`zp-banner-error ${className}`.trim()} role="alert" id={id} tabIndex={-1}>
      <svg className="zp-banner-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path
          fillRule="evenodd"
          d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z"
          clipRule="evenodd"
        />
      </svg>
      <div className="zp-banner-content">
        {title && <strong className="zp-banner-title">{title}</strong>}
        <p className="zp-banner-text">{error}</p>
      </div>
      {onDismiss && (
        <button
          type="button"
          className="zp-banner-dismiss"
          onClick={onDismiss}
          aria-label="Dismiss message"
        >
          <X size={16} />
        </button>
      )}
    </div>
  );
}

export function StatusMessage({ kind = 'info', children, className = '' }) {
  if (!children) return null;
  return (
    <div
      className={`zp-status-banner zp-status-${kind} ${className}`.trim()}
      role={kind === 'error' ? 'alert' : 'status'}
    >
      {kind === 'success' && (
        <svg className="zp-status-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
            clipRule="evenodd"
          />
        </svg>
      )}
      {kind === 'info' && (
        <svg className="zp-status-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z"
            clipRule="evenodd"
          />
        </svg>
      )}
      <div className="zp-status-body">{children}</div>
    </div>
  );
}

export function LoadingButton({
  children,
  loading = false,
  disabled = false,
  type = 'submit',
  onClick,
  className = '',
  icon,
  loadingLabel = 'Processing…',
  ...props
}) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      aria-busy={loading}
      onClick={onClick}
      className={`zp-primary-button ${className} ${loading ? 'is-loading' : ''}`.trim()}
      {...props}
    >
      {loading ? (
        <>
          <span className="zp-btn-spinner" aria-hidden="true" />
          <span className="zp-btn-label">{loadingLabel}</span>
        </>
      ) : (
        <>
          <span className="zp-btn-label">{children}</span>
          {icon && (
            <span className="zp-btn-icon" aria-hidden="true">
              {icon}
            </span>
          )}
        </>
      )}
    </button>
  );
}

export const PrimaryButton = LoadingButton;

export function SecondaryButton({
  children,
  disabled = false,
  type = 'button',
  onClick,
  className = '',
  icon,
  ...props
}) {
  return (
    <button
      type={type}
      disabled={disabled}
      onClick={onClick}
      className={`zp-secondary-button ${className}`.trim()}
      {...props}
    >
      {icon && (
        <span className="zp-btn-icon" aria-hidden="true">
          {icon}
        </span>
      )}
      <span className="zp-btn-label">{children}</span>
    </button>
  );
}

export function ConsentCheckbox({
  id,
  checked,
  onChange,
  disabled = false,
  error,
  children,
}) {
  const errorId = error ? `${id}-error` : undefined;
  return (
    <div className={`zp-declaration-box ${error ? 'has-error' : ''}`}>
      <label className="zp-checkbox-label" htmlFor={id}>
        <input
          id={id}
          type="checkbox"
          checked={checked}
          onChange={onChange}
          disabled={disabled}
          aria-invalid={Boolean(error)}
          aria-describedby={errorId}
          className="zp-checkbox-input"
        />
        <span>{children}</span>
      </label>
      {error && (
        <p className="zp-field-error" id={errorId} role="alert">
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}

export function MembershipSummary({
  compact = true,
  feeLabel = '₹599 lifetime membership',
  detail = 'One-time fee (incl. GST), payable via Razorpay after contact verification. Membership and permanent Affiliate ID activate immediately after verified payment.',
}) {
  return (
    <div className={`zp-membership-summary ${compact ? 'is-compact' : ''}`}>
      <div className="zp-membership-summary-head">
        <span className="zp-membership-fee-pill">{feeLabel}</span>
      </div>
      <p className="zp-membership-summary-text">{detail}</p>
    </div>
  );
}
