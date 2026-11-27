import { Link } from 'react-router-dom';
import Icon from '../Icon';
import { currency, membershipBenefits } from '../benefits';
import './Howitworks.css';

const steps = [
  ['join', 'Join ZayloPe', `${currency(membershipBenefits.fee)} Membership`, 'Complete registration and applicable verification, then activate your membership.'],
  ['unlock', 'Unlock Benefits', 'Shopping Wallet + Coupons', `Access ${currency(membershipBenefits.wallet)} shopping wallet benefits and the proposed ${currency(membershipBenefits.coupons)} coupon allocation.`],
  ['share', 'Share & Promote', 'Share Savings', 'Share eligible Zaylo products and coupons with friends and family.'],
  ['earn', 'Earn Commissions', 'Qualifying Orders', 'Earn commissions on qualifying referral purchases after applicable approval conditions.'],
];

function JourneyArt({ kind }) {
  return <div className={`zp-journey-art zp-journey-${kind}`} aria-hidden="true">
    <svg viewBox="0 0 240 150" fill="none" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={`journey-green-${kind}`} x1="50" y1="30" x2="190" y2="130" gradientUnits="userSpaceOnUse"><stop stopColor="#15946b"/><stop offset="1" stopColor="#083d31"/></linearGradient>
        <linearGradient id={`journey-gold-${kind}`} x1="80" y1="20" x2="145" y2="90" gradientUnits="userSpaceOnUse"><stop stopColor="#fff0b5"/><stop offset="1" stopColor="#d8b85a"/></linearGradient>
      </defs>
      <ellipse cx="120" cy="133" rx="72" ry="8" fill="#083d31" opacity=".08"/>
      {kind === 'join' && <>
        <g className="journey-member-card"><rect x="48" y="37" width="146" height="91" rx="12" fill="#06402f"/><rect x="43" y="30" width="146" height="91" rx="12" fill={`url(#journey-green-${kind})`} stroke="#309671"/>
          <text x="57" y="51" fill="#fff" fontSize="13" fontWeight="800">ZayloPe</text><path d="M58 68h23v17H58Z" fill="#d4f578" opacity=".85"/><path d="M58 99h60m-60 8h37" stroke="#91c6aa" strokeWidth="3" strokeLinecap="round"/>
          <path className="journey-card-reflection" d="m118 30 39 91h24l-39-91Z" fill="#fff" opacity=".12"/>
        </g>
        <g className="journey-approved"><circle cx="183" cy="108" r="21" fill="#d4f578" stroke="#fff" strokeWidth="3"/><path className="journey-check" d="m173 108 7 7 13-14" stroke="#083d31" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/></g>
      </>}
      {kind === 'unlock' && <>
        <g className="journey-coupon"><path d="m128 28 66 9-3 19a7 7 0 0 0-2 14l-3 19-66-9 3-19a7 7 0 0 0 2-14Z" fill="#d4f578" stroke="#abc968"/><path d="m171 35-6 48" stroke="#6e8a3c" strokeDasharray="3 3"/></g>
        <g className="journey-wallet-lid"><path d="M52 74V52a10 10 0 0 1 10-10h93l12 38Z" fill="#0a513c"/><path d="m64 53 84-5 5 32-89 7Z" fill="#e7efd8"/></g>
        <g className="journey-coin"><circle cx="102" cy="46" r="18" fill="#b89a43"/><circle cx="99" cy="43" r="18" fill={`url(#journey-gold-${kind})`} stroke="#fff0b5"/><text x="99" y="50" textAnchor="middle" fill="#665220" fontSize="21" fontWeight="700">₹</text></g>
        <rect x="49" y="65" width="120" height="62" rx="12" fill={`url(#journey-green-${kind})`} stroke="#298662"/><path d="M60 76h89M60 115h89" stroke="#9cc8a9" strokeOpacity=".4" strokeDasharray="3 4"/>
        <rect x="143" y="83" width="36" height="26" rx="7" fill="#0b4936" stroke="#4a9970"/><circle cx="155" cy="96" r="3" fill="#d4f578"/>
      </>}
      {kind === 'share' && <>
        <path d="M112 77h63V40m-63 37h63v37" stroke="#8bb69c" strokeWidth="2" strokeDasharray="4 4"/>
        <rect x="57" y="20" width="67" height="112" rx="13" fill="#07412f"/><rect x="62" y="25" width="57" height="100" rx="9" fill="#ecf5df"/><path d="M79 29h23" stroke="#07412f" strokeWidth="4" strokeLinecap="round"/>
        <path d="m73 78 12 12 23-28" stroke="#008f63" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"/>
        <g className="journey-user"><circle cx="178" cy="35" r="22" fill="#d4f578"/><circle cx="178" cy="29" r="6" fill="#0e6044"/><path d="M167 46a11 11 0 0 1 22 0" fill="#0e6044"/></g>
        <g className="journey-user"><circle cx="178" cy="115" r="20" fill="#c8e4d1"/><circle cx="178" cy="109" r="5" fill="#0e6044"/><path d="M168 125a10 10 0 0 1 20 0" fill="#0e6044"/></g>
        <g className="journey-travelling-coupon"><path d="M98 56h39v9a5 5 0 0 0 0 10v9H98v-9a5 5 0 0 0 0-10Z" fill="#d4f578" stroke="#8ba94d"/><path d="M126 59v22" stroke="#74913f" strokeDasharray="2 3"/><text x="110" y="76" textAnchor="middle" fill="#24563a" fontSize="15" fontWeight="700">₹</text></g>
      </>}
      {kind === 'earn' && <>
        <g className="journey-bars" fill={`url(#journey-green-${kind})`}>
          {[['58', '91', '36'], ['99', '67', '60'], ['140', '41', '86']].map(([x, y, height]) => <g key={x}><rect x={x} y={y} width="27" height={height} rx="3"/><path d={`m${Number(x) + 27} ${y} 8-5v${height}l-8 5Z`} fill="#084b36"/></g>)}
        </g>
        <path d="m53 72 45-31 29 4 39-25m-16 0h16v16" stroke="#008f63" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/>
        <g className="journey-coin"><circle cx="185" cy="110" r="22" fill="#b89a43"/><circle cx="182" cy="107" r="22" fill={`url(#journey-gold-${kind})`} stroke="#fff0b5" strokeWidth="2"/><text x="182" y="115" textAnchor="middle" fill="#665220" fontSize="26" fontWeight="700">₹</text></g>
        <g className="journey-approved"><circle cx="193" cy="41" r="14" fill="#d4f578" stroke="#fff" strokeWidth="2"/><path className="journey-check" d="m187 41 4 4 8-9" stroke="#083d31" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></g>
      </>}
    </svg>
  </div>;
}

export default function HowItWorks() {
  return <section className="zp-section zp-how" id="how-it-works" aria-labelledby="how-title" data-reveal><div className="zp-shell">
    <header className="zp-heading"><p className="zp-eyebrow">Membership journey</p><h2 id="how-title">How ZayloPe Works</h2><p>Four simple steps. A more rewarding way to shop and share.</p></header>
    <ol className="zp-journey-grid">{steps.map(([kind, title, highlight, copy], index) => <li className="zp-journey-step" key={kind}>
      <div className="zp-journey-marker"><span>STEP <b>0{index + 1}</b></span></div>
      <div className="zp-journey-reveal"><article className="zp-journey-card">
        <JourneyArt kind={kind}/><h3>{title}</h3><strong className="zp-journey-highlight">{highlight}</strong><p>{copy}</p>
        {kind === 'unlock' && <small className="zp-journey-disclosure">Coupon allocation subject to business confirmation.</small>}
      </article></div>
    </li>)}</ol>
    <div className="zp-journey-cta"><Link className="zp-button zp-primary" to="/register">Get Started <Icon name="arrow"/></Link><small>Illustrations show the journey; benefits and commissions are subject to eligibility.</small></div>
  </div></section>;
}

