import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import JourneyArtwork from '../About/JourneyArtwork';
import Icon from '../Home/Icon';

import { howSteps as steps } from './howSteps';

function wrapStep(index) {
  return (index + steps.length) % steps.length;
}

export default function HowProcess() {
  const [active, setActive] = useState(0);
  const [paused, setPaused] = useState(false);
  const [inView, setInView] = useState(false);
  const [hovered, setHovered] = useState(false);
  const [mobile, setMobile] = useState(() => matchMedia('(width < 768px)').matches);
  const [reducedMotion, setReducedMotion] = useState(() => matchMedia('(prefers-reduced-motion: reduce)').matches);
  const frame = useRef(null);
  const start = useRef(null);
  const autoplay = mobile && inView && !reducedMotion && !paused && !hovered;

  useEffect(() => {
    const width = matchMedia('(width < 768px)');
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const syncWidth = () => setMobile(width.matches);
    const syncMotion = () => setReducedMotion(motion.matches);
    const observer = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting));
    observer.observe(frame.current);
    width.addEventListener('change', syncWidth);
    motion.addEventListener('change', syncMotion);
    return () => {
      observer.disconnect();
      width.removeEventListener('change', syncWidth);
      motion.removeEventListener('change', syncMotion);
    };
  }, []);

  useEffect(() => {
    if (!autoplay) return;
    const timer = window.setInterval(() => {
      if (!document.hidden) setActive(index => wrapStep(index + 1));
    }, 5500);
    return () => window.clearInterval(timer);
  }, [autoplay]);

  function selectStep(index) {
    setPaused(true);
    setActive(wrapStep(index));
  }

  function finishSwipe(event) {
    if (!start.current || start.current.id !== event.pointerId) return;
    const dx = event.clientX - start.current.x;
    const dy = event.clientY - start.current.y;
    start.current = null;
    if (Math.abs(dx) >= 45 && Math.abs(dx) > Math.abs(dy) * 1.2) {
      selectStep(active + (dx < 0 ? 1 : -1));
    }
  }

  return <>
    <nav className="how-index" aria-label="Steps at a glance">
      <div className="public-container tw:grid">
        {steps.map(({ title }, index) => <a key={title} href={'#how-step-' + (index + 1)}>
          <span className="how-number">0{index + 1}</span>{title}
        </a>)}
      </div>
    </nav>

    <section id="how-process" ref={frame} className="how-process" aria-label="The planned affiliate process"
      aria-roledescription={mobile ? 'carousel' : undefined}
      onFocusCapture={event => { if (mobile && event.target.matches(':focus-visible')) setPaused(true); }}
      onPointerEnter={event => setHovered(event.pointerType === 'mouse')}
      onPointerLeave={() => setHovered(false)}>
      <div className="public-container">
        <div className="how-slides" onPointerDown={event => {
          if (!mobile || !event.isPrimary) return;
          setPaused(true);
          if (event.pointerType === 'mouse' || event.target.closest('a')) return;
          event.currentTarget.setPointerCapture(event.pointerId);
          start.current = { id: event.pointerId, x: event.clientX, y: event.clientY };
        }} onPointerUp={finishSwipe} onPointerCancel={() => { start.current = null; }}>
          {steps.map(({ kind, title, copy }, index) => <article key={kind} id={'how-step-' + (index + 1)}
            className="how-step" data-kind={kind} data-active={index === active} aria-labelledby={'how-title-' + kind}>
            <div className="how-illustration"><JourneyArtwork kind={kind} /></div>
            <div className="how-step-copy">
              <p className="how-kicker">Step {index + 1} of {steps.length}</p>
              <h2 id={'how-title-' + kind}>{title}</h2>
              <p>{copy}</p>
              {kind === 'user' && <Link className="how-membership-link" to="/membership">Explore Membership <Icon name="arrow" /></Link>}
            </div>
          </article>)}
        </div>

        {mobile && <div className="how-controls">
          <div className="how-indicators tw:flex" role="group" aria-label="Choose a step">
              {steps.map(({ title }, index) => <button key={title} type="button" aria-label={'Show step ' + (index + 1) + ', ' + title}
                aria-current={index === active ? 'step' : undefined} onClick={() => selectStep(index)} />)}
          </div>
          <div className="how-control-row tw:flex tw:items-center tw:justify-between">
            <button type="button" className="how-arrow how-prev" aria-label="Previous step" onClick={() => selectStep(active - 1)}><Icon name="arrow" /> Previous</button>
            {!reducedMotion && <button type="button" className="how-play" onClick={() => setPaused(value => !value)}>
              {paused ? 'Resume slideshow' : 'Pause slideshow'}
            </button>}
            <button type="button" className="how-arrow" aria-label="Next step" onClick={() => selectStep(active + 1)}>Next <Icon name="arrow" /></button>
          </div>
          <span className="tw:sr-only" role="status">{paused ? `Step ${active + 1} of ${steps.length}. ${steps[active].title}. ${steps[active].copy}` : ''}</span>
          <Link className="zp-button zp-primary how-mobile-start" to="/register">Get Started <Icon name="arrow" /></Link>
        </div>}
      </div>
    </section>
  </>;
}
