import React, { useState } from 'react';
import { FormInput, LoadingButton, SecondaryButton } from './FormControls';
import './BankDetailsForm.css';

export default function BankDetailsForm({
  bankData,
  onChange,
  consent,
  onConsentChange,
  errors = {},
  onSubmit,
  onBack,
  disabled = false,
  loading = false,
}) {
  const [showAccountNumber, setShowAccountNumber] = useState(false);

  const handleIfscChange = (e) => {
    const uppercaseVal = e.target.value.toUpperCase().replace(/\s/g, '');
    onChange({
      target: {
        name: 'ifsc_code',
        value: uppercaseVal,
      },
    });
  };

  const handleAccountChange = (e) => {
    const numeric = e.target.value.replace(/\D/g, '');
    onChange({
      target: {
        name: e.target.name,
        value: numeric,
      },
    });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSubmit(e);
  };

  return (
    <form className="zp-bank-form" onSubmit={handleSubmit} noValidate>
      <div className="zp-bank-notice">
        <svg className="zp-bank-notice-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fillRule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clipRule="evenodd" />
        </svg>
        <p>
          Please ensure these bank details match the name on your identity documents for timely payout settlements.
        </p>
      </div>

      {bankData.masked_account_number && (
        <div style={{
          padding: '0.75rem 1rem',
          backgroundColor: '#eff6ff',
          border: '1px solid #bfdbfe',
          borderRadius: '8px',
          color: '#1e40af',
          fontSize: '0.875rem',
          lineHeight: '1.4'
        }}>
          <strong>Saved Payout Account:</strong> {bankData.masked_account_number}
          <div style={{ fontSize: '0.75rem', color: '#3b82f6', marginTop: '0.25rem' }}>
            Leave account number blank to keep this account, or enter a new account number below to update.
          </div>
        </div>
      )}

      <FormInput
        id="account-holder-name"
        label="Account Holder Name"
        name="account_holder_name"
        value={bankData.account_holder_name}
        onChange={onChange}
        placeholder="Enter name as in bank records"
        autoComplete="name"
        required
        disabled={disabled || loading}
        error={errors.account_holder_name}
      />

      <FormInput
        id="bank-name"
        label="Bank Name"
        name="bank_name"
        value={bankData.bank_name}
        onChange={onChange}
        placeholder="e.g. State Bank of India, HDFC Bank, ICICI Bank"
        required
        disabled={disabled || loading}
        error={errors.bank_name}
      />

      <FormInput
        id="account-number"
        label="Bank Account Number"
        name="account_number"
        type={showAccountNumber ? 'text' : 'password'}
        value={bankData.account_number}
        onChange={handleAccountChange}
        placeholder={bankData.masked_account_number ? "Enter new account number (or leave blank to keep saved)" : "9 to 18 digit account number"}
        inputMode="numeric"
        maxLength={18}
        autoComplete="off"
        required={!bankData.masked_account_number}
        disabled={disabled || loading}
        error={errors.account_number}
        suffix={
          <button
            type="button"
            className="zp-toggle-mask-btn"
            onClick={() => setShowAccountNumber(!showAccountNumber)}
            aria-label={showAccountNumber ? 'Hide account number' : 'Show account number'}
          >
            {showAccountNumber ? (
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path fillRule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.268 5.943 14.478 3 10 3a9.958 9.958 0 00-4.512 1.074l-1.78-1.781zm4.261 4.26l1.514 1.515a2.003 2.003 0 012.45 2.45l1.514 1.514a4 4 0 00-5.478-5.478z" clipRule="evenodd" />
                <path d="M12.454 16.697L9.75 13.992a4 4 0 01-3.742-3.741L2.335 6.578A9.98 9.98 0 00.458 10c1.274 4.057 5.065 7 9.542 7 .847 0 1.669-.105 2.454-.303z" />
              </svg>
            ) : (
              <svg viewBox="0 0 20 20" fill="currentColor">
                <path d="M10 12a2 2 0 100-4 2 2 0 000 4z" />
                <path fillRule="evenodd" d="M.458 10C1.732 5.943 5.522 3 10 3s8.268 2.943 9.542 7c-1.274 4.057-5.064 7-9.542 7S1.732 14.057.458 10zM14 10a4 4 0 11-8 0 4 4 0 018 0z" clipRule="evenodd" />
              </svg>
            )}
          </button>
        }
      />

      <FormInput
        id="confirm-account-number"
        label="Confirm Account Number"
        name="confirm_account_number"
        type="password"
        value={bankData.confirm_account_number}
        onChange={handleAccountChange}
        placeholder={bankData.masked_account_number ? "Confirm new account number (or leave blank)" : "Re-enter bank account number"}
        inputMode="numeric"
        maxLength={18}
        autoComplete="off"
        required={!bankData.masked_account_number && Boolean(bankData.account_number)}
        disabled={disabled || loading}
        error={errors.confirm_account_number}
      />

      <FormInput
        id="ifsc-code"
        label="IFSC Code"
        name="ifsc_code"
        value={bankData.ifsc_code}
        onChange={handleIfscChange}
        placeholder="e.g. SBIN0001234"
        maxLength={11}
        autoComplete="off"
        required
        disabled={disabled || loading}
        error={errors.ifsc_code}
        helperText="11 characters alphanumeric branch code"
      />

      {/* Accuracy Consent */}
      <div className={`zp-bank-consent-row ${errors.consent ? 'has-error' : ''}`}>
        <label className="zp-checkbox-label">
          <input
            id="bank-consent-checkbox"
            type="checkbox"
            checked={consent}
            onChange={(e) => onConsentChange(e.target.checked)}
            disabled={disabled || loading}
            className="zp-checkbox-input"
          />
          <span>
            I confirm that the bank details provided above are accurate and belong to me.
          </span>
        </label>
        {errors.consent && (
          <p className="zp-field-error" role="alert">
            <span>{errors.consent}</span>
          </p>
        )}
      </div>

      {/* Buttons */}
      <div className="zp-form-action-buttons">
        {onBack && (
          <SecondaryButton onClick={onBack} disabled={disabled || loading}>
            Back
          </SecondaryButton>
        )}
        <LoadingButton loading={loading} disabled={disabled}>
          Continue to Review
        </LoadingButton>
      </div>
    </form>
  );
}
