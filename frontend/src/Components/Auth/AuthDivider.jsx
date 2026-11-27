import React from 'react';
import './AuthDivider.css';

export default function AuthDivider({ text = 'or continue with mobile / email' }) {
  return (
    <div className="zp-auth-divider" role="separator" aria-label={text}>
      <span className="zp-auth-divider-line" aria-hidden="true" />
      <span className="zp-auth-divider-text">{text}</span>
      <span className="zp-auth-divider-line" aria-hidden="true" />
    </div>
  );
}
