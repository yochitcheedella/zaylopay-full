import { useId } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../Home/Icon';
import { CommerceObject } from './CommerceHeroScene';
import './TrustCommitment.css';

const principles = [
  ['ticket', 'Understand the membership', 'Review membership details and applicable conditions before registering.'],
  ['shield', 'Know what qualifies', 'Commissions depend on eligibility, order status, delivery, returns and approved programme terms.'],
  ['share', 'Share responsibly', 'Be clear about affiliate relationships. Recommend thoughtfully, without income promises.'],
  ['controls', 'Stay in control', 'Registration includes verification and explicit consent before document submission.'],
];

function PrincipleIcon({ name }) {
  if (name === 'shield') return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m12 3 8 3v6c0 5-5 8-8 9-3-1-8-4-8-9V6z"/><path d="m8 12 3 3 5-6"/></svg>;
  if (name === 'controls') return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true"><path d="M3 7h7m5 0h6M3 17h12m5 0h1"/><circle cx="12.5" cy="7" r="2.5"/><circle cx="17.5" cy="17" r="2.5"/></svg>;
  return <Icon name={name}/>;
}

function TrustArtwork() {
  const prefix = useId();
  return <svg className="trust-art" viewBox="0 0 360 210" width="360" height="210" role="img" aria-labelledby={`${prefix}-title ${prefix}-description`}>
    <title id={`${prefix}-title`}>Trust and transparent commerce</title>
    <desc id={`${prefix}-description`}>A shield connects a membership document, shopping bag and referral link. An illustration of guiding principles, not a security certification.</desc>
    <defs><linearGradient id={`${prefix}-shield`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#20a674"/><stop offset="1" stopColor="#083d31"/></linearGradient></defs>
    <ellipse cx="180" cy="185" rx="135" ry="15" fill="#083d31" opacity=".07"/>
    <path d="M85 78h190M95 146h165" stroke="#8dbb9f" strokeWidth="2"/>
    <g transform="rotate(-8 91 76)"><rect x="40" y="30" width="102" height="91" rx="13" fill="#aecbb8"/><rect x="36" y="25" width="102" height="91" rx="13" fill="#fff" stroke="#b6cfbf"/><path d="M54 50h48m-48 17h63m-63 17h39" stroke="#6e9a81" strokeWidth="5" strokeLinecap="round"/></g>
    <g transform="translate(20 99) scale(.8)"><CommerceObject kind="bag"/></g>
    <g transform="translate(255 54) scale(.8)"><CommerceObject kind="link"/></g>
    <path d="m126 45 60-23 60 23v63c0 42-32 69-60 82-28-13-60-40-60-82Z" fill="#083d31"/>
    <path d="m120 39 60-23 60 23v63c0 42-32 69-60 82-28-13-60-40-60-82Z" fill={`url(#${prefix}-shield)`} stroke="#9bcfac" strokeWidth="2"/>
    <path d="m132 49 48-18 48 18v53c0 34-24 57-48 70-24-13-48-36-48-70Z" fill="none" stroke="#c1e0b5" strokeOpacity=".65"/>
    <g transform="translate(157 70) scale(1.9)" fill="none" stroke="#e3f5c7" strokeWidth="1.8" strokeLinecap="round"><path d="m10 8 3-3a4 4 0 0 1 6 6l-3 3m-2 2-3 3a4 4 0 0 1-6-6l3-3m1 5 6-6"/></g>
  </svg>;
}

export default function TrustCommitment() {
  return <section className="about-container trust-section" id="our-commitment" aria-labelledby="about-trust-title" data-about-reveal>
    <div className="trust-layout">
      <div className="trust-intro">
        <p className="about-eyebrow">06 / Our Commitment</p>
        <h2 id="about-trust-title">Trust is part<br/><span>of the experience.</span></h2>
        <p className="trust-lead">Clear expectations make better connections. These are the principles guiding the experience we’re building.</p>
        <figure className="trust-illustration"><TrustArtwork/></figure>
        <Link className="trust-faq-link" to="/membership#membership-faq">Read the membership FAQs <Icon name="arrow"/></Link>
      </div>
      <ol className="trust-cards" aria-label="Our four commitments">{principles.map(([icon,title,description],index)=><li className="trust-reveal" key={title} style={{'--trust-index':index}}><article className="trust-card"><span className="trust-card-icon"><PrincipleIcon name={icon}/></span><h3>{title}</h3><p>{description}</p></article></li>)}</ol>
    </div>
  </section>;
}
