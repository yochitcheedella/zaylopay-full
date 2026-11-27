export function FormField({ id, label, error, prefix, className = '', ...props }) {
  const input = <input {...props} id={id} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-error` : undefined}/>;
  return <div className={`registration-field ${className}`}><label htmlFor={id}>{label}</label>{prefix ? <div className={`registration-mobile ${error ? 'is-invalid' : ''}`}><span aria-hidden="true">{prefix}</span>{input}</div> : input}{error && <small className="registration-field-error" id={`${id}-error`}>{error}</small>}</div>;
}

export function PrimaryButton({ children, loading = false, disabled, ...props }) {
  return <button type="submit" {...props} disabled={disabled || loading} aria-busy={loading}>{loading && <span className="registration-spinner" aria-hidden="true"/>}{children}</button>;
}

export function StatusAlert({ children, kind = 'info', className = '', ...props }) {
  return <div role={kind === 'error' ? 'alert' : 'status'} className={`status-alert ${className}`} {...props}>{children}</div>;
}

export function ProgressStepper({ steps, compactSteps = steps, currentStep, onStep, disabled }) {
  return <nav className="register-progress" aria-label="Registration progress">{steps.map((step, index) => {
    const number = index + 1;
    return <button type="button" key={step} className={currentStep === number ? 'active-step' : currentStep > number ? 'completed-step' : ''} onClick={() => onStep(number)} aria-label={`${number}. ${step}`} aria-current={currentStep === number ? 'step' : undefined} disabled={disabled}><span className="registration-step-number" aria-hidden="true">{currentStep > number ? '✓' : number}</span><span>{compactSteps[index] || step}</span></button>;
  })}</nav>;
}
