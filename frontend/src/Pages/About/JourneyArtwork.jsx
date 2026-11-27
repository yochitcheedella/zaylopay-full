import { useId } from 'react';
import { CommerceObject } from './CommerceHeroScene';

// Extend the project's original vector objects into coherent visual stages.
// These are educational illustrations, never live products or account data.
export default function JourneyArtwork({ kind }) {
  const id = useId();
  const labels = {
    user: 'A ZayloPe membership card illustrates the first step of the programme.',
    bag: 'A shopping bag beside a product showcase represents eligible product discovery.',
    link: 'A product card and chain link represent a personal affiliate referral.',
    share: 'Connected community figures represent thoughtful product sharing.',
    check: 'An order card and a verification shield represent a purchase that must qualify.',
    reward: 'A wallet and rupee coin represent a commission subject to eligibility and approval.',
  };
  const object = (name,x,y,scale) => <g transform={`translate(${x} ${y}) scale(${scale})`}><CommerceObject kind={name}/></g>;
  return <svg className="aj-art" viewBox="0 0 360 280" width="360" height="280" role="img" aria-labelledby={`${id}-title`}>
    <title id={`${id}-title`}>{labels[kind]}</title>
    <defs><linearGradient id={`${id}-surface`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#ffffff"/><stop offset="1" stopColor="#dceedd"/></linearGradient></defs>
    <ellipse cx="180" cy="245" rx="126" ry="16" fill="#083d31" opacity=".07"/>
    {kind === 'user' && <g transform="rotate(-6 180 140)">
      <rect x="44" y="59" width="272" height="166" rx="19" fill="#86ac99"/>
      <rect x="40" y="52" width="272" height="166" rx="19" fill="var(--zp-forest)"/>
      <path d="M219 53a91 91 0 0 0 90 91m-107-91a108 108 0 0 0 107 108" fill="none" stroke="#49785f" strokeWidth="3"/>
      <text x="65" y="102" fill="white" fontFamily="var(--zp-font-heading)" fontSize="28" fontWeight="700">ZayloPe</text>
      <text x="65" y="185" fill="var(--zp-lime)" fontFamily="var(--zp-font-body)" fontSize="17" letterSpacing="2">MEMBERSHIP</text>
    </g>}
    {kind === 'bag' && <><g transform="rotate(7 230 130)"><rect x="177" y="35" width="126" height="178" rx="20" fill="#bed6c5"/><rect x="171" y="28" width="126" height="178" rx="20" fill={`url(#${id}-surface)`} stroke="#a9c7b5"/><rect x="187" y="46" width="94" height="104" rx="12" fill="#b9dfc2"/><path d="M211 105V86a24 24 0 0 1 48 0v19m-48-9h8v25h-8zm40 0h8v25h-8z" fill="none" stroke="#08734e" strokeWidth="8" strokeLinejoin="round"/><path d="M191 170h68m-68 14h44" stroke="#53806b" strokeWidth="5" strokeLinecap="round"/></g>{object('bag',29,65,1.9)}</>}
    {kind === 'link' && <><rect x="47" y="33" width="168" height="201" rx="20" fill="#aec9b7"/><rect x="41" y="26" width="168" height="201" rx="20" fill={`url(#${id}-surface)`} stroke="#a9c7b5"/><rect x="57" y="42" width="136" height="116" rx="12" fill="#c3e1c7"/>{object('bag',86,48,.95)}<path d="M63 179h100m-100 19h67" stroke="#719e88" strokeWidth="6" strokeLinecap="round"/>{object('link',153,97,1.65)}</>}
    {kind === 'share' && <><path d="M62 133h236M180 35v198" stroke="#a5c9b3" strokeWidth="2" strokeDasharray="5 8"/>{object('share',63,24,2.4)}<circle cx="69" cy="198" r="18" fill="#fff" stroke="#aac8b5"/><path d="m61 198 5 5 11-12" fill="none" stroke="#08734e" strokeWidth="3" strokeLinecap="round"/></>}
    {kind === 'check' && <><rect x="42" y="43" width="160" height="183" rx="18" fill="#adcbb8"/><rect x="36" y="36" width="160" height="183" rx="18" fill={`url(#${id}-surface)`} stroke="#a9c7b5"/><path d="M65 76h98m-98 24h66m-66 60h80m-80 20h57" stroke="#88b59b" strokeWidth="6" strokeLinecap="round"/><path d="m157 80 66-25 66 25v68c0 50-39 77-66 89-27-12-66-39-66-89Z" fill="#083d31"/><path d="m151 72 66-25 66 25v68c0 50-39 77-66 89-27-12-66-39-66-89Z" fill="#159263" stroke="#9cd3ac" strokeWidth="3"/><path d="m183 135 24 24 44-53" fill="none" stroke="#d4f578" strokeWidth="10" strokeLinecap="round" strokeLinejoin="round"/></>}
    {kind === 'reward' && <>{object('reward',59,26,2.4)}<g><circle cx="285" cy="193" r="27" fill="#fff" stroke="#a9c7b5"/><path d="m272 193 9 9 17-21" fill="none" stroke="#08734e" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"/></g></>}
  </svg>;
}
