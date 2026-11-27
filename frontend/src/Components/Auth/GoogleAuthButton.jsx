import React, { useEffect, useRef, useState } from 'react';
import './GoogleAuthButton.css';

/**
 * Singleton script loader to guarantee Google Identity Services (GSI)
 * script is loaded reliably and only once across the application.
 */
let googleScriptPromise = null;

function loadGoogleIdentityServices() {
  if (typeof window !== 'undefined' && window.google?.accounts?.id) {
    return Promise.resolve();
  }
  if (googleScriptPromise) {
    return googleScriptPromise;
  }

  googleScriptPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector(
      'script[src="https://accounts.google.com/gsi/client"]'
    );

    if (existingScript) {
      if (window.google?.accounts?.id) {
        resolve();
        return;
      }
      existingScript.addEventListener('load', () => resolve(), { once: true });
      existingScript.addEventListener(
        'error',
        () => reject(new Error('Failed to load Google Identity Services SDK')),
        { once: true }
      );

      // Polling fallback in case load event fired before listener was attached
      let checks = 0;
      const interval = setInterval(() => {
        if (window.google?.accounts?.id) {
          clearInterval(interval);
          resolve();
        } else if (++checks > 50) {
          clearInterval(interval);
          reject(new Error('Google Identity Services load timeout'));
        }
      }, 100);
      return;
    }

    const script = document.createElement('script');
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => {
      googleScriptPromise = null;
      reject(new Error('Failed to load Google Identity Services SDK'));
    };
    document.head.appendChild(script);
  });

  return googleScriptPromise;
}

/**
 * Maps custom button text prop to Google's supported renderButton text parameters.
 * Supported values: 'signin_with' | 'signup_with' | 'continue_with' | 'signin'
 */
function mapButtonText(text) {
  if (!text) return 'continue_with';
  const lower = text.toLowerCase();
  if (lower.includes('sign up') || lower.includes('signup') || lower.includes('register')) {
    return 'signup_with';
  }
  if (lower.includes('continue')) {
    return 'continue_with';
  }
  return 'signin_with';
}

/**
 * Official Google-branded authentication button using Google Identity Services renderButton().
 * - Replaces custom prompt() / One Tap invocation with official explicit Google Sign-In button flow.
 * - Handles React StrictMode lifecycle without duplicate buttons or stale callbacks.
 * - Responsive container with touch-accessible dimensions.
 */
export default function GoogleAuthButton({
  onSuccess,
  onError,
  loading = false,
  text = 'Continue with Google',
  disabled = false,
  className = '',
}) {
  const buttonContainerRef = useRef(null);
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID;
  const [sdkError, setSdkError] = useState(() => (!clientId ? 'Google Client ID is not configured.' : ''));
  const [sdkReady, setSdkReady] = useState(false);

  // Maintain fresh callback refs to avoid stale closures without re-initializing GIS
  const onSuccessRef = useRef(onSuccess);
  const onErrorRef = useRef(onError);
  useEffect(() => {
    onSuccessRef.current = onSuccess;
    onErrorRef.current = onError;
  });

  useEffect(() => {
    let isMounted = true;
    let resizeObserver = null;
    let lastRenderedWidth = 0;

    if (!clientId) {
      onErrorRef.current?.('Google Client ID is not configured.');
      return;
    }

    const renderGoogleButton = () => {
      const container = buttonContainerRef.current;
      if (!isMounted || !container || !window.google?.accounts?.id) return;
      const measuredWidth = Math.floor(container.clientWidth || container.offsetWidth || 360);
      const clampedWidth = Math.min(Math.max(measuredWidth, 200), 400);
      if (Math.abs(clampedWidth - lastRenderedWidth) < 8 && container.childElementCount > 0) {
        return;
      }
      lastRenderedWidth = clampedWidth;
      container.innerHTML = '';
      window.google.accounts.id.renderButton(container, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text: mapButtonText(text),
        shape: 'rectangular',
        logo_alignment: 'left',
        width: clampedWidth,
      });
    };

    loadGoogleIdentityServices()
      .then(() => {
        if (!isMounted || !buttonContainerRef.current) return;

        try {
          window.google.accounts.id.initialize({
            client_id: clientId,
            callback: (response) => {
              if (response?.credential) {
                onSuccessRef.current?.(response.credential);
              } else {
                onErrorRef.current?.('No credential returned from Google.');
              }
            },
            auto_select: false,
            cancel_on_tap_outside: true,
          });

          renderGoogleButton();

          if (typeof ResizeObserver !== 'undefined' && buttonContainerRef.current) {
            resizeObserver = new ResizeObserver(() => renderGoogleButton());
            resizeObserver.observe(buttonContainerRef.current);
          }

          if (isMounted) {
            setSdkReady(true);
          }
        } catch {
          if (!isMounted) return;
          const msg = 'Failed to initialize Google Sign-In button.';
          setSdkError(msg);
          onErrorRef.current?.(msg);
        }
      })
      .catch(() => {
        if (!isMounted) return;
        const msg = 'Google sign-in could not load. Please try again or use your email or mobile number.';
        setSdkError(msg);
        onErrorRef.current?.(msg);
      });

    return () => {
      isMounted = false;
      resizeObserver?.disconnect();
    };
  }, [clientId, text]);

  return (
    <div className={`zp-google-btn-wrapper ${className} ${disabled ? 'is-disabled' : ''}`}>
      {sdkError ? (
        <div className="zp-google-btn-error" role="alert">
          <p className="zp-google-btn-error-text">{sdkError}</p>
        </div>
      ) : (
        <div
          ref={buttonContainerRef}
          className="zp-google-btn-container"
          data-testid="google-auth-button-container"
          aria-label={text}
        />
      )}

      {loading && (
        <div className="zp-google-btn-overlay" role="status" aria-live="polite">
          <span className="zp-google-btn-spinner" aria-hidden="true" />
          <span>Verifying with Google…</span>
        </div>
      )}

      {!sdkReady && !sdkError && !loading && (
        <div className="zp-google-btn-placeholder" aria-hidden="true">
          <span className="zp-google-btn-spinner" />
          <span>Loading Google Sign-In…</span>
        </div>
      )}
    </div>
  );
}


