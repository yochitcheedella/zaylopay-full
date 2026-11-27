import { useState } from 'react';
import { SlidersHorizontal, ArrowDown } from 'lucide-react';
import { CountUp } from '../../motion';
import { calculatorDefaults, currency, estimateCommission } from '../../Pages/Home/benefits';
import Icon from '../../Pages/Home/Icon';
import './EarningsCalculator.css';

const controls = [
  { key: 'orders', label: 'Monthly qualifying orders', min: 0, max: 200, step: 1, unit: 'orders', icon: 'bag' },
  { key: 'value', label: 'Average eligible order value', min: 0, max: 10000, step: 1, unit: '₹', icon: 'wallet' },
  { key: 'rate', label: 'Example commission rate', min: 0, max: 20, step: .5, unit: '%', icon: 'reward' },
];

const controlValue = (control, raw) => {
  const number = Number(raw);
  if (!Number.isFinite(number)) return control.min;
  return Math.min(control.max, Math.max(control.min, Math.round(number / control.step) * control.step));
};
const formatValue = (control, value) => control.key === 'value' ? currency(value) : `${value.toLocaleString('en-IN')}${control.unit === '%' ? '%' : ''}`;

function CommissionVisual() {
  return <div className="zp-calc-visual" aria-hidden="true"><div className="zp-calc-visual-tilt">
    <svg viewBox="0 0 240 110" fill="none" focusable="false">
      <defs><linearGradient id="calc-coin-gold" x1="90" y1="15" x2="150" y2="90" gradientUnits="userSpaceOnUse"><stop stopColor="#f0ffbd"/><stop offset="1" stopColor="#aeca60"/></linearGradient></defs>
      <ellipse cx="120" cy="99" rx="70" ry="7" fill="#031e18" opacity=".4"/>
      <g className="zp-calc-bars"><rect x="48" y="68" width="18" height="28" rx="3" fill="#498568"/><rect x="76" y="53" width="18" height="43" rx="3" fill="#6ca77c"/><rect x="157" y="36" width="18" height="60" rx="3" fill="#579369"/><rect x="185" y="17" width="18" height="79" rx="3" fill="#75ad7b"/></g>
      <path d="m42 51 38-24 25 5 35-20" stroke="#b1d779" strokeWidth="2" strokeLinecap="round" opacity=".65"/>
      <g className="zp-calc-coin"><circle cx="128" cy="58" r="35" fill="#7d9e43"/><circle cx="123" cy="54" r="35" fill="url(#calc-coin-gold)" stroke="#e9ffb1" strokeWidth="2"/><circle cx="123" cy="54" r="28" stroke="#739743" strokeOpacity=".45"/><text x="123" y="67" textAnchor="middle" fill="#264d2d" fontSize="39" fontWeight="700">₹</text></g>
    </svg>
  </div></div>;
}

export default function EarningsCalculator() {
  const [values, setValues] = useState({ ...calculatorDefaults });
  const [drafts, setDrafts] = useState({});
  const update = (control, raw) => setValues(previous => ({ ...previous, [control.key]: controlValue(control, raw) }));
  const edit = (control, raw) => {
    setDrafts(previous => ({ ...previous, [control.key]: raw }));
    if (raw.trim() !== '' && Number.isFinite(Number(raw))) update(control, raw);
  };
  const commit = (control, raw) => {
    update(control, raw);
    setDrafts(previous => ({ ...previous, [control.key]: undefined }));
  };
  const total = estimateCommission(values);
  const qualifyingValue = values.orders * values.value;
  return <section className="zp-section zp-calculator" aria-labelledby="calculator-title" data-reveal><div className="zp-shell">
    <header className="zp-heading"><p className="zp-eyebrow">INTERACTIVE EARNINGS CALCULATOR</p><h2 id="calculator-title">How much can you earn in a month?</h2><p>Move the sliders or enter your own values to instantly estimate your potential monthly commissions.</p></header>
    <p className="zp-calc-instruction"><SlidersHorizontal size={18} aria-hidden="true"/>Try it yourself — adjust the values below.<ArrowDown size={16} aria-hidden="true"/></p>
    <div className="zp-calc-reveal"><div className="zp-calc-grid"><div className="zp-calc-controls"><div className="zp-calc-intro"><div><h3>Build your monthly estimate</h3><p>Adjust any value to see your earnings update.</p></div><button type="button" onClick={() => { setValues({ ...calculatorDefaults }); setDrafts({}); }}><svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M4 10a8 8 0 1 1 1 8M4 4v6h6"/></svg>Reset</button></div>
      {controls.map(control => {
        const progress = (values[control.key] - control.min) / (control.max - control.min) * 100;
        return <div className="zp-control" key={control.key}>
          <div className="zp-control-heading"><label htmlFor={`calc-${control.key}`}><span className="zp-calc-control-icon"><Icon name={control.icon}/></span>{control.label}</label>
            <span className="zp-number-input">{control.unit === '₹' && <span aria-hidden="true">₹</span>}<input id={`calc-${control.key}`} type="number" inputMode={control.key === 'rate' ? 'decimal' : 'numeric'} min={control.min} max={control.max} step={control.step} value={drafts[control.key] ?? values[control.key]} onChange={event => edit(control, event.target.value)} onBlur={event => commit(control, event.target.value)} aria-describedby={control.key === 'rate' ? 'calc-rate-note' : 'calc-value-note'}/>{control.unit !== '₹' && <span aria-hidden="true">{control.unit}</span>}</span>
          </div>
          <div className="zp-range-wrap" style={{ '--progress': `${progress}%`, '--thumb-offset': `${12 - progress * .24}px` }}>
            <span className="zp-range-tooltip" aria-hidden="true">{formatValue(control, values[control.key])}</span>
            <input type="range" aria-label={`${control.label} slider`} aria-valuetext={`${formatValue(control, values[control.key])}${control.key === 'orders' ? ' orders' : ''}`} aria-describedby={control.key === 'rate' ? 'calc-rate-note' : 'calc-value-note'} min={control.min} max={control.max} step={control.step} value={values[control.key]} onChange={event => commit(control, event.target.value)}/>
          </div><div className="zp-range-labels" aria-hidden="true"><span>{formatValue(control, control.min)}</span><span>{formatValue(control, control.max)}</span></div>
        </div>;
      })}
      <p className="zp-calc-input-note" id="calc-rate-note">Example rate only — not an approved commission offer.</p>
    </div><div className="zp-calc-result"><span className="zp-example-label"><span/>ILLUSTRATIVE ESTIMATE</span><CommissionVisual key={total}/><p className="zp-calc-result-label">Estimated monthly commission</p><output aria-live="polite" aria-atomic="true" aria-label={`Estimated monthly commission: ${currency(total)}`}><CountUp value={total} formatter={currency} duration={300}/></output>
      <dl className="zp-calc-summary"><div><dt>Qualifying order value</dt><dd>{currency(qualifyingValue)}</dd></div><div><dt>Example commission rate</dt><dd>{values.rate}%</dd></div></dl>
      <p className="zp-equation">{values.orders.toLocaleString('en-IN')} orders × {currency(values.value)} × {values.rate}% = <strong>{currency(total)}</strong></p>
      <small>Illustrative income, not guaranteed.<br/>Wallet and coupon benefits are not included.</small>
    </div></div></div>
    <p className="zp-calc-disclaimer" id="calc-value-note">Use commission-eligible order values confirmed by applicable business rules. No assumption is made about taxes, shipping or discounts. Actual commissions depend on eligibility, delivery, returns and approval conditions.</p>
  </div></section>;
}

