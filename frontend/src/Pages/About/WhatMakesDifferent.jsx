import { useId, useRef, useState } from 'react';
import Icon from '../Home/Icon';
import { CommerceObject } from './CommerceHeroScene';
import './WhatMakesDifferent.css';

const benefits = [
  { icon: 'link', title: 'A personal way to share', description: 'Planned personal affiliate links connect a recommendation with its qualifying referral.', nodes: [0,1,2], caption: 'Eligible products, personal links and thoughtful community sharing.', stages: 'Product · Referral link · Community' },
  { icon: 'check', title: 'Clarity before commission', description: 'The intended tracking experience will distinguish order status, eligibility and approved commission.', nodes: [3,4], caption: 'An attributed order must qualify before a commission can be approved.', stages: 'Qualifying order · Eligible commission' },
  { icon: 'user', title: 'A considered member journey', description: 'Start with registration and verification. Membership activation and commerce features are separate future steps.', nodes: [0,3], caption: 'Registration and verification come first. Commerce participation is a separate future step.', stages: 'Member access · Eligibility checks' },
];
const nodes = [
  { kind: 'bag', label: 'Eligible product', x: 26, y: 20 },
  { kind: 'link', label: 'Personal link', x: 272, y: 20 },
  { kind: 'share', label: 'Community sharing', x: 148, y: 154 },
  { kind: 'check', label: 'Qualifying order', x: 26, y: 288 },
  { kind: 'reward', label: 'Eligible commission', x: 272, y: 288 },
];

function EcosystemArtwork({ active, prefix }) {
  const highlighted = benefits[active].nodes;
  return <svg className="different-art" viewBox="0 0 480 432" width="480" height="432" role="img" aria-labelledby={`${prefix}-art-title ${prefix}-art-description`}>
    <title id={`${prefix}-art-title`}>The intended ZayloPe ecosystem</title>
    <desc id={`${prefix}-art-description`}>An eligible product connects to a personal link, community sharing, a qualifying order and an eligible commission. Highlighted: {benefits[active].stages}. This is an illustration of planned features.</desc>
    <defs><linearGradient id={`${prefix}-tile`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#fff"/><stop offset="1" stopColor="#eff8ee"/></linearGradient></defs>
    <g className="different-connections" fill="none" stroke="#9abca7" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path className={active===0?'is-lit':''} d="M182 76H272"/>
      <path className={active===0?'is-lit':''} d="M350 132v22q0 21-22 21h-24"/>
      <path className={active!==0?'is-lit':''} d="M226 266v11q0 19-22 19h-21"/>
      <path className={active===1?'is-lit':''} d="M182 344H272"/>
    </g>
    {nodes.map(({kind,label,x,y},index)=><g key={kind} className={`different-node ${highlighted.includes(index)?'is-lit':''}`} data-node={index} transform={`translate(${x} ${y})`}>
      <ellipse cx="78" cy="108" rx="70" ry="10" fill="#083d31" opacity=".06"/>
      <rect className="different-node-surface" x="0" y="0" width="156" height="114" rx="18" fill={`url(#${prefix}-tile)`} stroke="#bbd2c2"/>
      <g className="different-object" transform="translate(42 5) scale(.72)"><CommerceObject kind={kind}/></g>
      <text x="78" y="101" textAnchor="middle" fill="#083d31" fontFamily="Inter,Arial,sans-serif" fontSize="11" fontWeight="600">{label}</text>
      <circle className="different-node-indicator" cx="141" cy="15" r="4" fill="#008f63"/>
    </g>)}
  </svg>;
}

export default function WhatMakesDifferent() {
  const [active,setActive] = useState(0);
  const buttons = useRef([]);
  const prefix = useId();
  const activate = index => { setActive(index); buttons.current[index]?.focus({preventScroll:true}); };
  return <section className="about-container different-section" id="zaylope-difference" aria-labelledby="about-why-title" data-about-reveal data-about-surface="warm">
    <header className="different-heading"><p className="about-eyebrow">04 / What Makes ZayloPe Different</p><h2 id="about-why-title">Designed for a better<br/><span>affiliate experience.</span></h2></header>
    <div className="different-layout">
      <figure id={`${prefix}-showcase`} className="different-showcase" aria-labelledby={`${prefix}-showcase-title`}>
        <div><p className="about-eyebrow">One Ecosystem</p><h3 id={`${prefix}-showcase-title`}>A closer connection to the products you share.</h3></div>
        <EcosystemArtwork active={active} prefix={prefix}/>
        <div className={`different-member ${active===2?'is-lit':''}`}><Icon name="user"/><span>Member access begins with verification.</span></div>
        <figcaption><p className="different-active-caption" aria-live="polite">{benefits[active].caption}</p><ol className="different-legend" aria-label="Illustration stages">{nodes.map((node,index)=><li key={node.kind} className={benefits[active].nodes.includes(index)?'is-lit':''}>{node.label}</li>)}</ol><p className="different-disclaimer about-supporting-note">Planned Zaylo shopping experience. No live referral tracking. Orders must qualify and commissions require programme approval. Commissions and payouts are not guaranteed.</p></figcaption>
      </figure>
      <div className="different-features" role="group" aria-label="Explore what makes ZayloPe different">
        {benefits.map((benefit,index)=><article key={benefit.title} className={`different-feature ${active===index?'is-active':''}`} onPointerEnter={event=>{if(event.pointerType==='mouse')setActive(index);}}>
          <h3><button type="button" ref={el=>{buttons.current[index]=el;}} aria-pressed={active===index} aria-describedby={`${prefix}-feature-${index}`} aria-controls={`${prefix}-showcase`} onClick={()=>setActive(index)} onFocus={()=>setActive(index)} onKeyDown={event=>{
            const offsets={ArrowDown:1,ArrowRight:1,ArrowUp:-1,ArrowLeft:-1};
            if(event.key in offsets){event.preventDefault();activate((index+offsets[event.key]+benefits.length)%benefits.length);}
            else if(event.key==='Home'||event.key==='End'){event.preventDefault();activate(event.key==='Home'?0:benefits.length-1);}
          }}><span className="different-feature-icon"><Icon name={benefit.icon}/></span><span>{benefit.title}</span></button></h3>
          <p id={`${prefix}-feature-${index}`}>{benefit.description}</p>
          <div className="different-feature-meta"><span>{benefit.stages}</span><span className="different-selected">{active===index?'Selected':'Explore'}<Icon name={active===index?'check':'arrow'}/></span></div>
        </article>)}
      </div>
    </div>
  </section>;
}
