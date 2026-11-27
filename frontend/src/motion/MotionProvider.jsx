import { useEffect, useMemo, useState } from 'react';
import { MotionContext, useMotion } from './motionContext';

const STORAGE_KEY = 'zaylope:motion-paused';

function readStoredPause() {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'true';
  } catch {
    return false;
  }
}

export function MotionProvider({ children }) {
  const [reducedMotion, setReducedMotion] = useState(() =>
    typeof window !== 'undefined' && typeof window.matchMedia === 'function'
      ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
      : false
  );
  const [motionPaused, setMotionPaused] = useState(readStoredPause);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return undefined;
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReducedMotion(media.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  useEffect(() => {
    const syncVisibility = () => {
      document.documentElement.dataset.zpPageHidden = document.hidden ? 'true' : 'false';
    };
    syncVisibility();
    document.addEventListener('visibilitychange', syncVisibility);
    return () => document.removeEventListener('visibilitychange', syncVisibility);
  }, []);

  useEffect(() => {
    document.documentElement.dataset.zpMotion = motionPaused ? 'paused' : 'active';
    try {
      window.localStorage.setItem(STORAGE_KEY, String(motionPaused));
    } catch {
      // Ignore storage errors in restricted browsing contexts.
    }
  }, [motionPaused]);

  const value = useMemo(
    () => ({
      reducedMotion,
      motionPaused,
      effectivelyPaused: reducedMotion || motionPaused,
      toggleMotionPaused: () => setMotionPaused(prev => !prev),
    }),
    [reducedMotion, motionPaused]
  );

  return <MotionContext.Provider value={value}>{children}</MotionContext.Provider>;
}

export function MotionPauseButton({ className = '' }) {
  const { reducedMotion, motionPaused, toggleMotionPaused } = useMotion();
  if (reducedMotion) return null;

  return (
    <button
      type="button"
      className={`zp-motion-toggle ${className}`.trim()}
      aria-pressed={motionPaused}
      aria-label={motionPaused ? 'Resume background motion' : 'Pause background motion'}
      onClick={toggleMotionPaused}
    >
      <span aria-hidden="true">{motionPaused ? '▶' : '❚❚'}</span>
      <span>{motionPaused ? 'Resume motion' : 'Pause motion'}</span>
    </button>
  );
}
