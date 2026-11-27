import ReferralIllustration from './ReferralIllustrations';
import './Howtoearn.css';

const journey = [
  ['discover', 'Discover Products', 'Explore eligible products from the Zaylo eCommerce platform.'],
  ['share', 'Share Coupons', 'Share eligible discount coupons with friends and family.'],
  ['save', 'Friends Save', 'Friends apply a valid coupon when purchasing eligible products.'],
  ['earn', 'Earn Commission', 'Commissions depend on qualifying referred purchases and applicable delivery, return and approval conditions.'],
];
export default function HowToEarn() {
  return <section className="zp-section zp-earn" aria-labelledby="earn-title" data-reveal><div className="zp-shell">
    <header className="zp-heading"><h2 id="earn-title">How to Earn with ZayloPe</h2><p>Turn everyday recommendations into earning opportunities.</p></header>
    <ol className="zp-referral-grid">{journey.map(([kind,title,copy],index) => <li className="zp-referral-step" key={kind}>
      <div className="zp-referral-reveal"><article className="zp-referral-card">
        <span className="zp-referral-number">STEP <b>0{index+1}</b></span>
        <div className="zp-referral-visual"><div className="zp-referral-tilt"><ReferralIllustration kind={kind}/></div></div>
        <h3>{title}</h3><p>{copy}</p>
      </article></div>
      {index < journey.length-1 && <svg className="zp-referral-connector" viewBox="0 0 24 12" aria-hidden="true"><path d="M1 6h20m-5-4 5 4-5 4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round"/></svg>}
    </li>)}</ol>
  </div></section>;
}
