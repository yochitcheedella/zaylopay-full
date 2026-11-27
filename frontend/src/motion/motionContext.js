import { createContext, useContext } from 'react';

export const MotionContext = createContext({
  reducedMotion: false,
  motionPaused: false,
  effectivelyPaused: false,
  toggleMotionPaused: () => {},
});

export function useMotion() {
  return useContext(MotionContext);
}
