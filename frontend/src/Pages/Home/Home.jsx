import { useEffect, useRef } from 'react';
import { Link } from 'react-router-dom';
import { useViewportObserver } from '../../motion';
import HeroMembershipVisual from './HeroMembershipVisual';
import Icon from './Icon';
import HowItWorks from './Howitworks/Howitworks';
import EarningsCalculator from '../../Components/EarningsCalculator/EarningsCalculator';
import HowToEarn from '../../Components/Howtoearn/Howtoearn';
import { currency, membershipBenefits as benefits } from './benefits';
import './Home.css';

function BenefitArtwork({ kind }) {
  return <div className={`zp-benefit-art zp-benefit-art-${kind}`} aria-hidden="true">
    <svg viewBox="0 0 280 160" fill="none" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={`benefit-green-${kind}`} x1="65" y1="35" x2="220" y2="150" gradientUnits="userSpaceOnUse"><stop stopColor="#15946b"/><stop offset="1" stopColor="#083d31"/></linearGradient>
        <linearGradient id={`benefit-lime-${kind}`} x1="85" y1="30" x2="180" y2="130" gradientUnits="userSpaceOnUse"><stop stopColor="#ecffbe"/><stop offset="1" stopColor="#b6d964"/></linearGradient>
      </defs>
      <ellipse cx="140" cy="139" rx="78" ry="9" fill="#083d31" opacity=".09"/>
      {kind === 'wallet' && <>
        <g className="benefit-wallet-lid"><path d="M68 67V48a12 12 0 0 1 12-12h111l14 37Z" fill="#0a543e"/><path d="m80 48 101-7 6 41-107 8Z" fill="#ecf5dc"/><path d="m87 55 85-6" stroke="#b8cda6" strokeWidth="3"/></g>
        <g className="benefit-coin benefit-coin-one"><circle cx="113" cy="48" r="20" fill="#8fac43"/><circle cx="110" cy="45" r="20" fill={`url(#benefit-lime-${kind})`} stroke="#ecffbd" strokeWidth="2"/><text x="110" y="52" textAnchor="middle" fill="#315328" fontSize="22" fontWeight="700">₹</text></g>
        <g className="benefit-coin benefit-coin-two"><circle cx="167" cy="34" r="16" fill="#a3bf56"/><circle cx="165" cy="31" r="16" fill={`url(#benefit-lime-${kind})`} stroke="#ecffbd" strokeWidth="2"/><text x="165" y="37" textAnchor="middle" fill="#315328" fontSize="18" fontWeight="700">₹</text></g>
        <path d="M66 64a12 12 0 0 1 12-12h119a12 12 0 0 1 12 12v61a12 12 0 0 1-12 12H78a12 12 0 0 1-12-12Z" fill={`url(#benefit-green-${kind})`} stroke="#147a59"/>
        <path d="M76 68h118M76 125h118" stroke="#d4f578" strokeOpacity=".2" strokeDasharray="3 4"/>
        <rect x="177" y="81" width="43" height="30" rx="9" fill="#0c513b" stroke="#4b9c75"/><circle cx="190" cy="96" r="4" fill="#d4f578"/>
        <path d="M84 94h25m-25 8h17" stroke="#88c4a0" strokeWidth="3" strokeLinecap="round"/>
      </>}
      {kind === 'ticket' && <>
        <g className="benefit-ticket-back"><path d="M65 54h145v23a10 10 0 0 0 0 20v23H65V97a10 10 0 0 0 0-20Z" fill={`url(#benefit-green-${kind})`}/><path d="M177 59v55" stroke="#7ab993" strokeDasharray="4 4"/></g>
        <g className="benefit-ticket-front"><path d="M78 39h141v25a10 10 0 0 0 0 20v25H78V84a10 10 0 0 0 0-20Z" fill={`url(#benefit-lime-${kind})`} stroke="#a1c362"/><path d="M185 44v60" stroke="#779848" strokeDasharray="4 4"/><text x="100" y="79" fill="#083d31" fontSize="32" fontWeight="800">₹</text><path d="M133 62h33m-33 10h24m-24 10h30" stroke="#477344" strokeWidth="4" strokeLinecap="round"/><path d="m199 60 6 5-6 5m0 9 6 5-6 5" stroke="#5b803e" strokeWidth="2" strokeLinecap="round"/></g>
        <g className="benefit-sparkles" stroke="#008f63" strokeWidth="2" strokeLinecap="round"><path d="M49 40v10m-5-5h10M234 100v12m-6-6h12M225 26v8m-4-4h8"/></g>
      </>}
      {kind === 'growth' && <>
        <g className="benefit-bars" fill={`url(#benefit-green-${kind})`}>
          <g><path d="m85 101 13-7 21 7v33H85Z" fill="#176c4b"/><path d="M85 101h21v33H85Z"/><path d="m106 101 13-7v33l-13 7Z" fill="#084b36"/></g>
          <g><path d="m126 76 13-7 21 7v58h-34Z" fill="#25865c"/><path d="M126 76h21v58h-21Z"/><path d="m147 76 13-7v58l-13 7Z" fill="#084b36"/></g>
          <g><path d="m167 49 13-7 21 7v85h-34Z" fill="#4aab76"/><path d="M167 49h21v85h-21Z"/><path d="m188 49 13-7v85l-13 7Z" fill="#084b36"/></g>
        </g>
        <path className="benefit-growth-arrow" d="m76 80 46-33 27 6 48-31m-21 0h21v21" stroke="#008f63" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"/>
        <g className="benefit-coin benefit-coin-one"><circle cx="211" cy="115" r="22" fill="#93b44e"/><circle cx="208" cy="112" r="22" fill={`url(#benefit-lime-${kind})`} stroke="#e5ffae" strokeWidth="2"/><text x="208" y="120" textAnchor="middle" fill="#315328" fontSize="25" fontWeight="700">₹</text></g>
      </>}
    </svg>
  </div>;
}

function FinalMembershipCTA() {
  const panel = useRef(null);
  const frame = useRef(null);
  useEffect(() => {
    const motion = matchMedia('(prefers-reduced-motion: no-preference) and (hover: hover) and (pointer: fine)');
    const reset = () => {
      cancelAnimationFrame(frame.current);
      panel.current?.style.setProperty('--final-x', '0px');
      panel.current?.style.setProperty('--final-y', '0px');
    };
    motion.addEventListener('change', reset);
    return () => { cancelAnimationFrame(frame.current); motion.removeEventListener('change', reset); };
  }, []);
  const move = event => {
    if (event.pointerType !== 'mouse' || !matchMedia('(prefers-reduced-motion: no-preference) and (hover: hover) and (pointer: fine)').matches) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - bounds.left) / bounds.width - .5) * 12;
    const y = ((event.clientY - bounds.top) / bounds.height - .5) * 10;
    cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      panel.current?.style.setProperty('--final-x', `${x}px`);
      panel.current?.style.setProperty('--final-y', `${y}px`);
    });
  };
  return <section className="zp-final" aria-labelledby="final-title" data-reveal><div className="zp-shell">
    <div className="zp-final-reveal"><div className="zp-final-panel" ref={panel} onPointerMove={move} onPointerLeave={() => {
      cancelAnimationFrame(frame.current);
      panel.current?.style.setProperty('--final-x', '0px');
      panel.current?.style.setProperty('--final-y', '0px');
    }}>
      <div className="zp-final-decoration zp-final-decoration-left" aria-hidden="true"><div className="zp-final-parallax"><div className="zp-final-float">
        <svg viewBox="0 0 200 240" fill="none" focusable="false">
          <defs><linearGradient id="final-card-green" x1="20" y1="50" x2="170" y2="180" gradientUnits="userSpaceOnUse"><stop stopColor="#21a576"/><stop offset="1" stopColor="#0d503d"/></linearGradient><linearGradient id="final-coin-gold" x1="40" y1="155" x2="95" y2="220" gradientUnits="userSpaceOnUse"><stop stopColor="#fff1bb"/><stop offset="1" stopColor="#d5b256"/></linearGradient></defs>
          <ellipse cx="100" cy="217" rx="74" ry="10" fill="#021e17" opacity=".3"/>
          <g className="zp-final-member-art"><rect x="24" y="62" width="153" height="104" rx="13" transform="rotate(-12 24 62)" fill="#052e24"/><g transform="rotate(-12 20 54)"><rect x="20" y="54" width="153" height="104" rx="13" fill="url(#final-card-green)" stroke="#5dba8c"/><text x="34" y="78" fill="white" fontSize="16" fontWeight="800">ZayloPe</text><rect x="35" y="92" width="24" height="18" rx="3" fill="#d4f578"/><path d="M35 130h62m-62 9h42" stroke="#9ed8b6" strokeWidth="3" strokeLinecap="round"/><path d="m99 54 42 104h25L124 54Z" fill="#fff" opacity=".1"/></g></g>
          <g className="zp-final-coins"><circle cx="66" cy="190" r="26" fill="#9a813b"/><circle cx="62" cy="187" r="26" fill="url(#final-coin-gold)" stroke="#fff0b7" strokeWidth="2"/><text x="62" y="197" textAnchor="middle" fill="#655222" fontSize="29" fontWeight="700">₹</text><circle cx="113" cy="207" r="19" fill="#b59948"/><circle cx="110" cy="204" r="19" fill="url(#final-coin-gold)" stroke="#fff0b7"/><text x="110" y="211" textAnchor="middle" fill="#655222" fontSize="22" fontWeight="700">₹</text></g>
          <path d="M157 191v10m-5-5h10M37 27v8m-4-4h8" stroke="#8ecda1" strokeWidth="2" strokeLinecap="round"/>
        </svg>
      </div></div></div>
      <div className="zp-final-content"><span className="zp-eyebrow">Your Journey Starts Here</span><h2 id="final-title">Shop. Share. Grow.<br/><span>With ZayloPe.</span></h2><p className="zp-final-description">Ready to join? Explore membership details or get started.</p>
        <ul className="zp-final-benefits">{[[benefits.fee, 'Membership'], [benefits.wallet, 'Shopping Wallet'], [benefits.coupons, 'Coupon Benefits']].map(([amount,label]) => <li key={label}><strong>{currency(amount)}</strong><span>{label}</span></li>)}</ul>
        <div className="zp-final-actions"><Link className="zp-button zp-lime" to="/register">Get Started <Icon name="arrow"/></Link><Link className="zp-button zp-final-secondary" to="/membership">Explore Membership <Icon name="arrow"/></Link></div>
        <small className="zp-final-disclosure">{benefits.couponStatus} coupon allocation, subject to business confirmation.<br/>Wallet and coupons are shopping benefits, not withdrawable earnings.</small>
      </div>
      <div className="zp-final-decoration zp-final-decoration-right" aria-hidden="true"><div className="zp-final-parallax"><div className="zp-final-float">
        <svg viewBox="0 0 200 240" fill="none" focusable="false"><defs><linearGradient id="final-coupon-lime" x1="50" y1="30" x2="165" y2="130" gradientUnits="userSpaceOnUse"><stop stopColor="#edffc2"/><stop offset="1" stopColor="#badc6a"/></linearGradient><linearGradient id="final-bag-green" x1="80" y1="120" x2="175" y2="220" gradientUnits="userSpaceOnUse"><stop stopColor="#29a876"/><stop offset="1" stopColor="#0a513b"/></linearGradient></defs>
          <ellipse cx="108" cy="218" rx="72" ry="10" fill="#021e17" opacity=".3"/>
          <g transform="rotate(12 100 80)"><path d="M34 46h135v21a9 9 0 0 0 0 18v21H34V85a9 9 0 0 0 0-18Z" fill="#779c42" transform="translate(4 7)"/><path d="M34 46h135v21a9 9 0 0 0 0 18v21H34V85a9 9 0 0 0 0-18Z" fill="url(#final-coupon-lime)" stroke="#d9f79d"/><path d="M136 50v52" stroke="#6d8e3b" strokeDasharray="4 4"/><text x="57" y="89" fill="#285535" fontSize="34" fontWeight="700">%</text><path d="M94 67h26m-26 10h20m-20 10h24" stroke="#709342" strokeWidth="3" strokeLinecap="round"/></g>
          <g className="zp-final-bag"><path d="m86 143-10 70h97l-9-70Z" fill="#063d2c" transform="translate(6 5)"/><path d="m86 143-10 70h97l-9-70Z" fill="url(#final-bag-green)" stroke="#319b6d"/><path d="M103 148v-13a22 22 0 0 1 44 0v13" stroke="#8ecea2" strokeWidth="4" strokeLinecap="round"/><path d="m111 178 10 10 19-23" stroke="#d4f578" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round"/></g>
          <path d="M28 157v12m-6-6h12M179 20v8m-4-4h8" stroke="#8ecda1" strokeWidth="2" strokeLinecap="round"/>
        </svg>
      </div></div></div>
    </div></div>
  </div></section>;
}

export default function Home() {
  const root = useRef(null);
  useViewportObserver(root, { selector: '[data-reveal]', threshold: 0.18 });
  return <main className="zp-home" id="main-content" ref={root}>
    <section className="zp-hero" aria-labelledby="hero-title" data-reveal><div className="zp-shell zp-hero-grid">
      <div className="zp-hero-copy"><p className="zp-eyebrow"><span/>One Membership. More Possibilities.</p>
        <h1 id="hero-title">Shop Smart.<br/><em>Share Savings.</em><br/>Earn Rewards.</h1>
        <p className="zp-lead">Explore ZayloPe’s {currency(benefits.fee)} membership model, with intended shopping wallet benefits, eligible coupon sharing and commissions on qualifying purchases.</p>
        <div className="zp-actions"><Link className="zp-button zp-primary" to="/register">Get Started <Icon name="arrow"/></Link><Link className="zp-button zp-secondary" to="/how-it-works">How It Works <Icon name="arrow"/></Link></div>
        <p className="zp-eligibility"><Icon name="check"/><span>Registration &amp; applicable verification required.<br />Membership activation and affiliate features are planned.</span></p>
      </div><HeroMembershipVisual/>
    </div></section>
    <section className="zp-section zp-benefits" aria-labelledby="benefits-title" data-reveal><div className="zp-shell">
      <header className="zp-heading"><p className="zp-eyebrow">Membership at a Glance</p><h2 id="benefits-title">One Membership. More Benefits.</h2><p>Unlock shopping, sharing and earning opportunities with a {currency(benefits.fee)} membership.</p></header>
      <div className="zp-benefit-grid">{[
        ['wallet', currency(benefits.wallet), 'Shopping Wallet', 'Shop eligible Zaylo products with your membership wallet.', 'Shop smarter.'],
        ['ticket', currency(benefits.coupons), 'Coupon Benefits', 'Share eligible coupons with friends and family.', 'Share savings.'],
        ['growth', 'Earn', 'Referral Commissions', 'Earn commissions on qualifying referred purchases.', 'Grow your earnings.'],
      ].map(([kind, value, title, copy, label]) => <div className="zp-benefit-reveal" key={kind}><article className="zp-benefit">
        <BenefitArtwork kind={kind}/>
        <div className="zp-benefit-value-row"><strong className="zp-benefit-value">{value}</strong>{kind === 'ticket' && <span className="zp-benefit-provisional">{benefits.couponStatus} allocation</span>}</div>
        <h3>{title}</h3><p>{copy}</p>
        {kind === 'ticket' && <small className="zp-benefit-confirmation">Subject to business confirmation.</small>}
        <span className="zp-benefit-support"><Icon name="check"/>{label}</span>
      </article></div>)}</div>
      <div className="zp-benefits-footer"><Link className="zp-button zp-primary" to="/membership">Explore Membership <Icon name="arrow"/></Link>
        <p className="zp-footnote">Wallet benefits and coupons are shopping benefits, not withdrawable commission income.</p>
      </div>
    </div></section>
    <HowItWorks/><EarningsCalculator/><HowToEarn/>
    <FinalMembershipCTA/>
  </main>;
}



