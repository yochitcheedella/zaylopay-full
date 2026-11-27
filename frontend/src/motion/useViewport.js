import { useEffect } from 'react';
import { useMotion } from './motionContext';

/**
 * Observes matching child sections/items inside a container using one shared
 * IntersectionObserver, ensuring initial above-the-fold and already-scrolled elements
 * enter cleanly without leaving nodes stuck hidden.
 */
export function useViewportObserver(
  containerRef,
  {
    selector = '[data-reveal]',
    enterClass = 'has-entered',
    activeClass = 'in-view',
    threshold = 0.08,
    rootMargin = '0px 0px -28px 0px',
    once = false,
  } = {}
) {
  const { reducedMotion } = useMotion();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return undefined;

    const nodes = [...container.querySelectorAll(selector)];
    if (!nodes.length) return undefined;

    if (reducedMotion || typeof window.IntersectionObserver !== 'function') {
      nodes.forEach(node => {
        if (enterClass) node.classList.add(enterClass);
        if (activeClass) node.classList.add(activeClass);
      });
      return undefined;
    }

    // Ensure elements already visible or scrolled past on initial mount enter immediately.
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight;
    nodes.forEach(node => {
      const rect = node.getBoundingClientRect();
      if (rect.top < viewportHeight * 0.98) {
        if (enterClass) node.classList.add(enterClass);
        if (activeClass && rect.bottom > 0) node.classList.add(activeClass);
      }
    });

    const observer = new IntersectionObserver(
      entries => {
        for (const entry of entries) {
          if (activeClass) {
            entry.target.classList.toggle(activeClass, entry.isIntersecting);
          }
          if (entry.isIntersecting) {
            if (enterClass) entry.target.classList.add(enterClass);
            if (once) observer.unobserve(entry.target);
          }
        }
      },
      { threshold, rootMargin }
    );

    nodes.forEach(node => observer.observe(node));
    return () => observer.disconnect();
  }, [containerRef, selector, enterClass, activeClass, threshold, rootMargin, once, reducedMotion]);
}
