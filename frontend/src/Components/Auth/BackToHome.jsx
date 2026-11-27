import React from 'react';
import { Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';

/**
 * Top-left React Router navigation control back to the public ZayloPe homepage.
 * Stays outside the authentication card and preserves progress safely.
 */
export default function BackToHome({ onBeforeNavigate, className = '' }) {
  const handleClick = (event) => {
    if (typeof onBeforeNavigate === 'function') {
      const allowed = onBeforeNavigate(event);
      if (allowed === false) {
        event.preventDefault();
      }
    }
  };

  return (
    <Link to="/" className={`zp-back-home ${className}`.trim()} onClick={handleClick}>
      <ArrowLeft className="zp-back-home-icon" size={18} strokeWidth={2.2} aria-hidden="true" />
      <span>Back to home</span>
    </Link>
  );
}
