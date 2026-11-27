import { useRef } from 'react';
import Icon from '../Home/Icon';

import { howSteps as steps } from './howSteps';

export default function HowJourney({ active, onSelect }) {
  const buttons = useRef([]);
  const note = useRef(null);
  const select = index => {
    onSelect(index);
    buttons.current[index]?.focus({ preventScroll: true });
  };
  const move = index => {
    onSelect(index);
    if (index === 0 || index === steps.length - 1) requestAnimationFrame(() => note.current?.focus({ preventScroll: true }));
  };
  return <div className="how-journey">
    <ol className="how-step-grid" aria-label="Steps in the planned affiliate process">
      {steps.map(({ kind, title, summary }, index) => <li key={title}>
        <button type="button" ref={el => { buttons.current[index] = el; }} aria-pressed={active === index} aria-controls="how-step-detail" onClick={() => onSelect(index)} onKeyDown={event => {
          const offsets = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 };
          if (event.key in offsets) { event.preventDefault(); select((index + offsets[event.key] + steps.length) % steps.length); }
          else if (event.key === 'Home' || event.key === 'End') { event.preventDefault(); select(event.key === 'Home' ? 0 : steps.length - 1); }
        }}>
          <span className="how-step-symbol"><Icon name={kind} /><span className="how-step-number">{index + 1}</span></span>
          <span className="how-step-text"><span className="how-step-title">{title}</span><span className="how-step-summary">{summary}</span></span>
        </button>
      </li>)}
    </ol>
    <div className="how-step-detail" id="how-step-detail" ref={note} tabIndex="-1">
      <div key={active} className="how-detail-copy" aria-live="polite" aria-atomic="true"><strong>{steps[active].title}</strong><p>{steps[active].copy}</p></div>
      <div className="how-journey-controls"><button type="button" className="how-button how-secondary" disabled={active === 0} onClick={() => move(active - 1)}>Previous</button><span aria-hidden="true">{active + 1} / {steps.length}</span><button type="button" className="how-button how-primary" disabled={active === steps.length - 1} onClick={() => move(active + 1)}>Next<Icon name="arrow" /></button></div>
    </div>
  </div>;
}

