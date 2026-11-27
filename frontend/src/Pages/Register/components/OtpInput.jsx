import React, { useEffect, useRef, useState } from 'react';
import './OtpInput.css';

/**
 * Accessible 6-digit OTP control backed by ONE logical <input> overlaid transparently
 * across 6 responsive visual digit cells.
 * Supports SMS autofill (one-time-code), full paste, numeric keyboard, native backspace,
 * screen-reader single announcement, error state, and verification success sweep.
 */
export default function OtpInput({
  channel = 'verification',
  length = 6,
  value = Array(6).fill(''),
  onChange,
  disabled = false,
  hasError = false,
  isSuccess = false,
}) {
  const inputRef = useRef(null);
  const [focused, setFocused] = useState(false);

  const codeString = Array.isArray(value)
    ? value.join('').replace(/\D/g, '').slice(0, length)
    : String(value || '').replace(/\D/g, '').slice(0, length);

  useEffect(() => {
    if (!disabled && inputRef.current) {
      inputRef.current.focus();
    }
  }, [disabled]);

  const emitCode = (raw) => {
    const clean = String(raw || '').replace(/\D/g, '').slice(0, length);
    const digits = Array.from({ length }, (_, idx) => clean[idx] || '');
    onChange?.(digits);
  };

  const handleChange = (event) => {
    emitCode(event.target.value);
  };

  const handlePaste = (event) => {
    event.preventDefault();
    const pasted = event.clipboardData?.getData('text') || '';
    const clean = pasted.replace(/\D/g, '').slice(0, length);
    if (clean) {
      emitCode(clean);
    }
  };

  const activeCellIndex = Math.min(codeString.length, length - 1);

  return (
    <div
      className={`zp-otp-group ${focused ? 'is-focused' : ''} ${hasError ? 'has-error' : ''} ${
        isSuccess ? 'is-success' : ''
      }`.trim()}
      onClick={() => !disabled && inputRef.current?.focus()}
    >
      <input
        ref={inputRef}
        type="text"
        inputMode="numeric"
        pattern="[0-9]*"
        autoComplete="one-time-code"
        maxLength={length}
        value={codeString}
        disabled={disabled}
        onChange={handleChange}
        onPaste={handlePaste}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        aria-label={`${channel} ${length}-digit verification code`}
        aria-invalid={Boolean(hasError)}
        data-testid="otp-code-input"
        className="zp-otp-logical-input"
      />

      <div className="zp-otp-cells" aria-hidden="true">
        {Array.from({ length }, (_, index) => {
          const digit = codeString[index] || '';
          const isCurrent = focused && !disabled && index === activeCellIndex;
          return (
            <div
              key={index}
              className={`zp-otp-cell ${digit ? 'is-filled' : ''} ${isCurrent ? 'is-active' : ''}`.trim()}
            >
              <span>{digit}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
