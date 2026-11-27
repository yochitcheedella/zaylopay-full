import React from 'react';
import { Link } from 'react-router-dom';
import AuthBackground from './AuthBackground';
import BackToHome from './BackToHome';
import { AuthCard } from './AuthComponents';
import './AuthLayout.css';

const CARD_SIZE_MAP = {
  register: 'zp-auth-card-register',   // 640px
  compact: 'zp-auth-card-compact',     // 560px
  payment: 'zp-auth-card-payment',     // 680px
  status: 'zp-auth-card-register',     // 640px
};

/**
 * Shared Unified Authentication & Registration Layout Shell.
 * Powers /register (4 steps + Google flows) and /login with:
 * - Full-page animated pastel green gradient & looping SVG curves
 * - Top-left back-to-home navigation (outside card)
 * - Expanded centered white card with 24px corners and automatic height
 * - Minimal footer with high-contrast privacy and support links
 */
export default function AuthLayout({
  title,
  subtitle,
  children,
  cardSize = 'compact', // 'register' | 'compact' | 'payment' | 'status'
  layoutVariant,
  cardClassName = '',
  contentClassName = '',
  cardHeader = null,
  cardPrefix = null,
  showBrandLogo = true,
  showFooter = true,
  onBeforeBackToHome,
}) {
  const resolvedSize =
    cardSize || (layoutVariant === 'wide' ? 'payment' : 'compact');
  const sizeClass = CARD_SIZE_MAP[resolvedSize] || CARD_SIZE_MAP.compact;

  return (
    <div className={`zp-auth-shell ${layoutVariant === 'login' ? 'zp-auth-login' : ''}`}>
      <a href="#main-content" className="zp-skip-link">
        Skip to main content
      </a>

      <AuthBackground />

      <div className="zp-auth-topbar">
        <BackToHome onBeforeNavigate={onBeforeBackToHome} />
      </div>

      <main id="main-content" tabIndex={-1} className="zp-auth-main">
        <div className={`zp-auth-content ${sizeClass}-wrap ${contentClassName}`.trim()}>
          <AuthCard
            title={title}
            subtitle={subtitle}
            headerPrefix={cardHeader}
            showBrandLogo={showBrandLogo}
            className={`${sizeClass} ${cardClassName}`.trim()}
          >
            {cardPrefix}
            {children}
          </AuthCard>

          {showFooter && (
            <AuthFooter />
          )}
        </div>
      </main>
    </div>
  );
}

export function AuthFooter() { return (
            <footer className="zp-auth-footer">
              <div className="zp-auth-security-badge">
                <svg viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path
                    fillRule="evenodd"
                    d="M10 1.944A11.954 11.954 0 012.166 5C2.056 5.649 2 6.319 2 7c0 5.225 3.34 9.67 8 11.317C14.66 16.67 18 12.225 18 7c0-.682-.057-1.35-.166-2.001A11.954 11.954 0 0110 1.944zM11 14a1 1 0 11-2 0 1 1 0 012 0zm0-7a1 1 0 10-2 0v3a1 1 0 102 0V7z"
                    clipRule="evenodd"
                  />
                </svg>
                <span>Protected by ZayloPe • Encrypted &amp; private</span>
              </div>
              <div className="zp-auth-support-links">
                <a href="mailto:support@zaylope.com" className="zp-auth-support-link">
                  Support
                </a>
                <span className="zp-auth-footer-dot" aria-hidden="true">•</span>
                <Link to="/about" className="zp-auth-support-link">
                  Privacy policy
                </Link>
                <span className="zp-auth-footer-dot" aria-hidden="true">•</span>
                <Link to="/about" className="zp-auth-support-link">
                  Terms of service
                </Link>
              </div>
            </footer>
); }
