import React from 'react';
import './RegistrationStepper.css';

/**
 * Compact four-segment registration progress indicator for ZayloPe.
 * Separates semantic ARIA progressbar status from interactive step navigation buttons
 * so assistive technologies never encounter nested interactive roles.
 */
export default function RegistrationStepper({
  steps = ['Account', 'Verify', 'Payment', 'Status'],
  compactSteps: _compactSteps = steps,
  currentStep = 1,
  maxAllowedStep = currentStep,
  onStepClick,
  disabled = false,
}) {
  const totalSteps = steps.length;
  const clampedCurrent = Math.min(Math.max(1, currentStep), totalSteps);
  const currentLabel = steps[clampedCurrent - 1] || '';

  return (
    <nav className="zp-segment-stepper" aria-label="Registration steps">
      <div className="zp-segment-header">
        <span className="zp-segment-counter">
          Step {clampedCurrent} of {totalSteps}
        </span>
        <span className="zp-segment-title">{currentLabel}</span>
      </div>

      <div
        className="zp-sr-progress"
        role="progressbar"
        aria-label="Registration progress"
        aria-valuenow={clampedCurrent}
        aria-valuemin={1}
        aria-valuemax={totalSteps}
        aria-valuetext={`Step ${clampedCurrent} of ${totalSteps}: ${currentLabel}`}
      />

      <div className="zp-segment-track">
        {steps.map((label, index) => {
          const stepNumber = index + 1;
          const isCompleted = clampedCurrent > stepNumber;
          const isActive = clampedCurrent === stepNumber;
          const isUnlocked = stepNumber <= maxAllowedStep;
          const isClickable = !disabled && isUnlocked && !isActive && typeof onStepClick === 'function';

          return (
            <button
              key={label}
              type="button"
              className={`zp-segment-pill ${
                isCompleted ? 'is-completed' : isActive ? 'is-active' : 'is-pending'
              } ${isClickable ? 'is-clickable' : ''}`}
              onClick={() => isClickable && onStepClick(stepNumber)}
              disabled={!isClickable}
              aria-current={isActive ? 'step' : undefined}
              aria-label={`Step ${stepNumber} of ${totalSteps}: ${label} (${
                isCompleted ? 'completed' : isActive ? 'current step' : 'pending'
              })`}
            >
              <span className="zp-segment-inner-fill" />
            </button>
          );
        })}
      </div>
    </nav>
  );
}
