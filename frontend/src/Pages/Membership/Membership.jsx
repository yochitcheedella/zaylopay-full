import { useRef } from 'react';
import { BadgeIndianRupee, Link2, PackageSearch, ShoppingBag } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useViewportObserver } from '../../motion';
import affiliateArt from '../../assets/ecosystem-affiliate.webp';
import affiliateArtSmall from '../../assets/ecosystem-affiliate-384.webp';
import { BrandMark } from '../../Components/PublicLayout/BrandLogo';
import { FaqAccordion } from '../About/ClarityFaq';
import Icon from '../Home/Icon';
import { currency, membershipBenefits } from '../Home/benefits';
import './Membership.css';

const fee = currency(membershipBenefits.fee);
const features = [
  [ShoppingBag, 'Product discovery', 'Find eligible Zaylo products to recommend.'],
  [Link2, 'Personal affiliate links', 'Connect a recommendation to your account with a personal link.'],
  [PackageSearch, 'Referral tracking', 'Follow purchases attributed to your link.'],
  [BadgeIndianRupee, 'Commission visibility', 'See approved commissions for qualifying orders.'],
];

const joiningSteps = [
  ['Register', 'Enter your name, mobile number and email.'],
  ['Verify contacts', 'Verify your mobile number and email address.'],
  ['Provide required details', 'Follow the document and bank-detail prompts available to you.'],
  ['Review and pay', 'Review the final fee and terms before paying.'],
  ['Activate membership', 'Activation would follow verification and approved payment.'],
];

const commissionStages = [
  ['link', 'Personal link'],
  ['bag', 'Attributed purchase'],
  ['check', 'Eligibility review'],
  ['reward', 'Approved commission'],
];

const conditions = [
  ['Eligibility', 'Applications are open. Final eligibility, activation criteria and affiliate ID timing await approval.'],
  ['Fee and access', 'The final fee, taxes, billing frequency and access period await approval.'],
  ['Wallet and coupons', `The proposed ${currency(membershipBenefits.wallet)} shopping credit and ${currency(membershipBenefits.coupons)} in coupons are for shopping only. Inclusion, eligible products and redemption rules await approval.`],
  ['Cancellation and refunds', 'Cancellation rights, refund eligibility and deadlines await a published policy.'],
  ['Commission and payouts', 'Only approved orders qualify. Rates, payout thresholds and timing await confirmation.'],
  ['Privacy and account care', 'Keep verification codes private. Review consent requests and submit documents or bank details only through onboarding.'],
];

const faqs = [
  { question: 'What is ZayloPe membership?', answer: 'It is a planned affiliate programme for recommending eligible Zaylo products. Approved members would be able to share personal links and follow qualifying purchases.' },
  { question: 'How much does membership cost?', answer: fee + ' is the proposed programme fee. The final amount and tax treatment await approval. You can start your application without making a payment here.' },
  { question: 'Is the fee recurring?', answer: 'The billing schedule and access period are still under review. One-time payment and lifetime access have not been confirmed.' },
  { question: 'What happens after I register?', answer: 'Verify your mobile and email, then follow the available document and bank-detail steps. Payment, activation and affiliate ID issuance are later stages and are currently unavailable.' },
  { question: 'How do affiliate commissions work?', answer: 'A purchase must be attributed to your personal link and approved after the order review. Delivery, returns, cancellations and programme conditions affect the outcome.' },
  { question: 'Are earnings guaranteed?', answer: 'No. Joining the programme, sharing a link or receiving clicks does not guarantee a purchase, commission or payout.' },
  { question: 'Can I cancel or request a refund?', answer: 'The cancellation and refund terms await approval. Review the published policy before deciding whether to pay when checkout becomes available.' },
];

function MembershipActions({ dark = false }) {
  return <div className="member-actions mem:flex mem:flex-wrap">
    <Link className={'zp-button ' + (dark ? 'zp-lime' : 'zp-primary')} to="/register">Get Started <Icon name="arrow" /></Link>
    <Link className={'zp-button ' + (dark ? 'member-outline' : 'zp-secondary')} to="/how-it-works">How It Works</Link>
  </div>;
}

export default function Membership() {
  const page = useRef(null);
  useViewportObserver(page, {
    selector: '.member-benefits article, .member-timeline li, .member-flow li, .member-conditions > div',
    enterClass: 'member-entered',
    activeClass: '',
    once: true,
  });
  return <main className="membership-page" ref={page}>
    <section className="member-hero" aria-labelledby="member-title">
      <div className="public-container member-section member-hero-grid mem:grid mem:items-center">
        <div className="member-hero-copy">
          <p className="member-eyebrow">ZayloPe Membership</p>
          <h1 id="member-title">Share your Zaylo finds.</h1>
          <p className="member-lead">ZayloPe’s planned membership connects your product recommendations to personal links and commissions on eligible orders.</p>
          <div className="member-fee"><strong>{fee}</strong><span>Proposed membership fee</span></div>
          <p className="member-fee-note">Final pricing and access terms await approval. Checkout is unavailable.</p>
          <MembershipActions />
        </div>
        <div className="member-art">
          <img src={affiliateArt} srcSet={`${affiliateArtSmall} 384w, ${affiliateArt} 768w`}
            sizes="(max-width: 600px) 52vw, (max-width: 1000px) 30vw, 300px"
            width="768" height="768" loading="eager" fetchPriority="high" alt="A referral link and community connections on a smartphone beside a wallet." />
          <div className="member-pass" aria-hidden="true">
            <b><BrandMark />ZayloPe</b>
            <span>Membership</span>
            <ShoppingBag className="member-pass-bag" />
          </div>
        </div>
      </div>
    </section>

    <section className="member-status" aria-label="Current membership terms">
      <div className="public-container member-status-inner">
        <dl>
          <div><dt>Available today</dt><dd>Registration and verification</dd></div>
          <div><dt>Access period</dt><dd>Pending approval</dd></div>
          <div><dt>Checkout</dt><dd>Unavailable</dd></div>
        </dl>
        <a className="member-text-link" href="#membership-conditions">Membership conditions <span aria-hidden="true">↓</span></a>
      </div>
    </section>

    <section className="member-tint" aria-labelledby="member-benefits-title">
      <div className="public-container member-section">
        <h2 id="member-benefits-title">Planned membership benefits</h2>
        <div className="member-benefits mem:grid">
          {features.map(([Symbol, title, description], index) => <article key={title} style={{ '--member-delay': `${index * 65}ms` }}>
            <span className="member-icon"><Symbol size={24} strokeWidth={1.75} aria-hidden="true" /></span>
            <h3>{title}</h3><p>{description}</p>
          </article>)}
        </div>
      </div>
    </section>

    <section className="public-container member-section" aria-labelledby="member-join-title">
      <h2 id="member-join-title">How to become a member</h2>
      <p className="member-intro">Start your application now. Payment and activation are planned.</p>
      <ol className="member-timeline mem:grid">
        {joiningSteps.map(([title, copy], index) => <li key={title}>
          <span className="member-stage" data-planned={index >= 3} aria-hidden="true">{index + 1}</span>
          <h3>{title}</h3><p>{index >= 3 && <strong className="member-planned">Planned. </strong>}{copy}</p>
        </li>)}
      </ol>
    </section>

    <section className="member-commission" aria-labelledby="member-commission-title">
      <div className="public-container member-section">
        <h2 id="member-commission-title">How commission works</h2>
        <ol className="member-flow mem:grid">
          {commissionStages.map(([icon, title], index) => <li key={icon} style={{ '--member-delay': index * 35 + 'ms' }}><Icon name={icon} />{title}</li>)}
        </ol>
        <p>Clicks alone earn no commission. Commission becomes payable only when an attributed order passes review under the programme’s delivery, cancellation and return rules.</p>
      </div>
    </section>

    <section className="public-container member-section" aria-labelledby="membership-conditions">
      <h2 id="membership-conditions" tabIndex={-1}>Before you pay</h2>
      <dl className="member-conditions mem:grid">
        {conditions.map(([title, copy]) => <div key={title}><dt>{title}</dt><dd>{copy}</dd></div>)}
      </dl>
    </section>

    <section className="member-tint" id="membership-faq" aria-labelledby="member-faq-title">
      <div className="public-container member-section member-faq-layout mem:grid">
        <h2 id="member-faq-title">Membership FAQs</h2>
        <FaqAccordion items={faqs} />
      </div>
    </section>

    <section className="member-final" aria-labelledby="member-final-title">
      <div className="public-container member-section">
        <div><h2 id="member-final-title">Ready to explore ZayloPe?</h2>
          <p>Review the membership details and begin your registration journey.</p>
          <small>Eligibility and approval are required. Earnings are not guaranteed.</small>
        </div>
        <MembershipActions dark />
      </div>
    </section>
  </main>;
}
