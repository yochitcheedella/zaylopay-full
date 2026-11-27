import { useEffect, useLayoutEffect, useRef } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';
import { scrollToPageTop } from './navigationScroll';

const publicPaths = new Set(['/', '/about', '/how-it-works', '/membership']);
const position = () => ({ x: window.scrollX, y: window.scrollY });
function anchorPosition(hash) {
  let id;
  try { id = decodeURIComponent(hash.slice(1)); } catch { return null; }
  const target = document.getElementById(id);
  if (!target) return null;
  const nav = document.querySelector('.navbar');
  const panel = nav?.querySelector('.nav-panel');
  const panelHeight = panel && getComputedStyle(panel).display !== 'contents' ? panel.getBoundingClientRect().height : 0;
  const offset = nav ? nav.getBoundingClientRect().height - panelHeight + 12 : 16;
  return { x: 0, y: Math.max(0, target.getBoundingClientRect().top + window.scrollY - offset) };
}

export default function ScrollManager() {
  const location = useLocation();
  const navigationType = useNavigationType();
  const previous = useRef(null);
  const latest = useRef({ x: 0, y: 0 });
  const positions = useRef(new Map());
  const captured = useRef(null);

  useEffect(() => {
    const oldMode = history.scrollRestoration;
    history.scrollRestoration = 'manual';
    const track = () => { latest.current = position(); };
    const remember = () => { if (previous.current) { positions.current.set(previous.current.key, position()); captured.current = previous.current.key; } };
    const sameDestination = event => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const link = event.target.closest?.('a[href]');
      if (!link || link.hasAttribute('download') || (link.target && link.target !== '_self')) return;
      const url = new URL(link.href, window.location.href);
      if (url.origin !== window.location.origin) return;
      remember();
      if (url.pathname === window.location.pathname && !url.hash && (link.matches('.brand') || link.closest('.nav-links'))) {
        scrollToPageTop();
        return;
      }
      if (url.pathname !== window.location.pathname || url.search !== window.location.search || url.hash !== window.location.hash) return;
      if (url.hash) {
        const target = anchorPosition(url.hash);
        if (target) window.scrollTo({ left: target.x, top: target.y, behavior: 'instant' });
      } else if (publicPaths.has(url.pathname)) scrollToPageTop();
    };
    window.addEventListener('scroll', track, { passive: true });
    window.addEventListener('pagehide', remember);
    window.addEventListener('popstate', remember);
    document.addEventListener('click', sameDestination, true);
    return () => {
      history.scrollRestoration = oldMode;
      window.removeEventListener('scroll', track);
      window.removeEventListener('pagehide', remember);
      window.removeEventListener('popstate', remember);
      document.removeEventListener('click', sameDestination, true);
    };
  }, []);

  useLayoutEffect(() => {
    const outgoing = previous.current;
    // A route commit can trigger browser scroll anchoring before layout effects.
    // Prefer the position captured before the navigation changed the DOM.
    if (outgoing && captured.current !== outgoing.key) positions.current.set(outgoing.key, latest.current);
    captured.current = null;
    previous.current = location;
    const unchangedDocument = outgoing?.pathname === location.pathname && outgoing?.hash === location.hash;
    // Query and registration step updates retain both scroll and the mounted form.
    if (unchangedDocument && navigationType !== 'POP') return;
    const saved = navigationType === 'POP' ? positions.current.get(location.key) : null;
    if (unchangedDocument && !saved) return;
    let stopped = false;
    let frame;
    const apply = () => {
      if (stopped) return;
      const desired = saved || (location.hash ? anchorPosition(location.hash) : { x: 0, y: 0 });
      if (!desired) return; // MutationObserver handles anchors rendered after route commit.
      window.scrollTo({ left: desired.x, top: desired.y, behavior: 'instant' });
      latest.current = position();
    };
    const schedule = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(apply); };
    const stop = () => { stopped = true; cancelAnimationFrame(frame); resize.disconnect(); mutations.disconnect(); };
    const keyStop = event => { if (!['Shift', 'Control', 'Alt', 'Meta'].includes(event.key)) stop(); };
    const resize = new ResizeObserver(schedule);
    const mutations = new MutationObserver(schedule);
    apply();
    // Layout and font/image completion can change the document after the route commits.
    if (saved || location.hash) resize.observe(document.body);
    const header = document.querySelector('.navbar');
    if (header && location.hash) resize.observe(header);
    if (saved || location.hash) mutations.observe(document.getElementById('root'), { childList: true, subtree: true });
    window.addEventListener('wheel', stop, { passive: true });
    window.addEventListener('touchstart', stop, { passive: true });
    window.addEventListener('pointerdown', stop, { passive: true });
    window.addEventListener('keydown', keyStop);
    window.addEventListener('zaylope:cancel-restoration', stop);
    return () => {
      stop();
      window.removeEventListener('wheel', stop);
      window.removeEventListener('touchstart', stop);
      window.removeEventListener('pointerdown', stop);
      window.removeEventListener('keydown', keyStop);
      window.removeEventListener('zaylope:cancel-restoration', stop);
    };
  }, [location, navigationType]);
  return null;
}



