import { useEffect, useRef, useState } from 'react';
import { useMotion } from './motionContext';

export default function CountUp({
  value,
  formatter = v => String(Math.round(v)),
  duration = 350,
  className,
}) {
  const { effectivelyPaused } = useMotion();
  const [display, setDisplay] = useState(value);
  const current = useRef(value);
  const hasMounted = useRef(false);

  useEffect(() => {
    if (!hasMounted.current) {
      hasMounted.current = true;
      current.current = value;
      return undefined;
    }

    if (effectivelyPaused || current.current === value) {
      const frame = requestAnimationFrame(() => {
        current.current = value;
        setDisplay(value);
      });
      return () => cancelAnimationFrame(frame);
    }

    const from = current.current;
    const start = performance.now();
    let frame = 0;

    const tick = now => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - (1 - progress) ** 3;
      const next = from + (value - from) * eased;
      current.current = next;
      setDisplay(next);
      if (progress < 1) {
        frame = requestAnimationFrame(tick);
      }
    };

    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [value, duration, effectivelyPaused]);

  return <span className={className} aria-hidden="true">{formatter(display)}</span>;
}
