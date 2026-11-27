import { useEffect, useId, useRef } from 'react';
import { BrandMark } from '../../Components/PublicLayout/BrandLogo';
import { affiliateJourney } from './aboutContent';
import './CommerceHeroScene.css';

// Original vector objects use the same top-left light and shallow extruded edges.
export function CommerceObject({ kind, className = '' }) {
  const id = useId().replaceAll(':', '');
  return <svg className={`commerce-object-art ${className}`} viewBox="0 0 100 100" width="100" height="100" fill="none" aria-hidden="true" focusable="false">
    <defs><linearGradient id={`${id}-green`} x1="20" y1="15" x2="80" y2="85" gradientUnits="userSpaceOnUse"><stop stopColor="#30ac80" /><stop offset="1" stopColor="var(--zp-forest)" /></linearGradient><linearGradient id={`${id}-mint`} x1="22" y1="12" x2="78" y2="82" gradientUnits="userSpaceOnUse"><stop stopColor="#f0f9df" /><stop offset="1" stopColor="#bed79f" /></linearGradient></defs>
    <ellipse cx="51" cy="88" rx="32" ry="6" fill="var(--zp-forest)" opacity=".1" />
    {kind === 'bag' && <><path d="m23 35 48-9 11 9 1 46-49 9-11-9Z" fill="var(--zp-forest)" /><path d="m23 35 48-9 4 49-47 9Z" fill={`url(#${id}-green)`} /><path d="m71 26 11 9 1 46-8-6Z" fill="#0a4a37" /><path d="M36 37V23c0-18 22-22 24-4l1 13" stroke="#b0d8aa" strokeWidth="5" strokeLinecap="round" /><path d="m37 51 24-4m-23 12 17-3" stroke="#d4f578" strokeWidth="2.5" strokeLinecap="round" /></>}
    {kind === 'link' && <><g stroke="#527b46" strokeWidth="13" strokeLinecap="round"><path d="m49 42 11-12a15 15 0 0 1 22 21L68 66" /><path d="m51 61-12 13a15 15 0 0 1-22-21l14-15" /></g><g stroke={`url(#${id}-mint)`} strokeWidth="11" strokeLinecap="round"><path d="m47 37 11-12a15 15 0 0 1 22 21L66 61" /><path d="m49 56-12 13a15 15 0 0 1-22-21l14-15" /></g><path d="m38 51 20-20" stroke="var(--zp-forest)" strokeWidth="5" strokeLinecap="round" /></>}
    {kind === 'share' && <><path d="m27 39 45-12m-45 16 39 29" stroke="#64997e" strokeWidth="4" /><g fill="var(--zp-forest)"><ellipse cx="25" cy="42" rx="18" ry="19" /><ellipse cx="74" cy="28" rx="17" ry="18" /><ellipse cx="69" cy="74" rx="17" ry="18" /></g>{[[23,37],[72,23],[67,69]].map(([x,y])=><g key={x}><circle cx={x} cy={y} r="17" fill={`url(#${id}-mint)`} /><circle cx={x} cy={y-4} r="4" fill="var(--zp-forest)" /><path d={`M${x-8} ${y+9}a8 8 0 0 1 16 0`} fill="var(--zp-forest)" /></g>)}</>}
    {kind === 'check' && <><circle cx="52" cy="50" r="33" fill="#83a374" /><circle cx="48" cy="44" r="33" fill={`url(#${id}-mint)`} stroke="#e9f7d9" strokeWidth="2" /><circle cx="48" cy="44" r="26" stroke="#8bb486" /><path d="m32 43 11 12 22-26" stroke="var(--zp-forest)" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" /></>}
    {kind === 'reward' && <><path d="M19 48h56a8 8 0 0 1 8 8v24H24a9 9 0 0 1-9-9V51Z" fill="var(--zp-forest)" /><path d="m19 39 54-8 5 39-54 8a9 9 0 0 1-9-9V48a9 9 0 0 1 4-9Z" fill={`url(#${id}-green)`} /><path d="M70 48h16v18H70a9 9 0 0 1 0-18Z" fill="#126348" /><circle cx="77" cy="57" r="2" fill="var(--zp-lime)" /><ellipse cx="45" cy="26" rx="21" ry="22" fill="#7e9e47" /><circle cx="42" cy="22" r="21" fill={`url(#${id}-mint)`} stroke="#e1f2bd" strokeWidth="2" /><text x="42" y="30" textAnchor="middle" fill="var(--zp-forest)" fontSize="25" fontWeight="700">₹</text></>}
  </svg>;
}

const labels = ['Discover products', 'Personal link', 'Community sharing', 'Qualifying purchase', 'Eligible commission'];
const hubPaths = ['M237 157 133 89', 'M294 156 387 78', 'M306 216 388 251', 'M217 236 133 277', 'M260 270V333'];
const flowPaths = ['M139 75C216 20 300 20 381 66', 'M452 137V208', 'M379 286C299 328 215 330 141 290', 'M132 322C170 362 180 381 194 381'];

export default function CommerceHeroScene() {
  const scene = useRef(null);
  const stage = useRef(null);
  const pointer = useRef({ frame: null, x: 0, y: 0 });
  const prefix = useId().replaceAll(':', '');
  useEffect(() => {
    const pointerState = pointer.current;
    const motion = matchMedia('(prefers-reduced-motion: reduce)');
    const reset = () => { cancelAnimationFrame(pointer.current.frame); pointer.current.frame = null; stage.current?.style.setProperty('--scene-x', '0deg'); stage.current?.style.setProperty('--scene-y', '0deg'); };
    motion.addEventListener('change', reset);
    const observer = 'IntersectionObserver' in window ? new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) { scene.current?.classList.remove('scene-visible'); reset(); }
      else scene.current?.classList.add('scene-visible');
    }, { threshold: .1 }) : null;
    if (scene.current) observer?.observe(scene.current);
    return () => { observer?.disconnect(); motion.removeEventListener('change', reset); cancelAnimationFrame(pointerState.frame); };
  }, []);
  const reset = () => { cancelAnimationFrame(pointer.current.frame); pointer.current.frame = null; stage.current?.style.setProperty('--scene-x', '0deg'); stage.current?.style.setProperty('--scene-y', '0deg'); };
  const tilt = event => {
    if (event.pointerType !== 'mouse' || !matchMedia('(min-width: 801px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)').matches) return;
    const rect = event.currentTarget.getBoundingClientRect();
    pointer.current.x = Math.max(-1, Math.min(1, (event.clientX-rect.left)/rect.width*2-1));
    pointer.current.y = Math.max(-1, Math.min(1, (event.clientY-rect.top)/rect.height*2-1));
    if (pointer.current.frame) return;
    pointer.current.frame = requestAnimationFrame(() => { stage.current?.style.setProperty('--scene-x', `${-pointer.current.y*3}deg`); stage.current?.style.setProperty('--scene-y', `${pointer.current.x*3}deg`); pointer.current.frame = null; });
  };
  return <figure ref={scene} className="commerce-scene" aria-labelledby={`${prefix}-caption`} onPointerMove={tilt} onPointerLeave={reset} onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) reset(); }}>
    <div className="commerce-scene-stage" ref={stage}>
      <div className="commerce-scene-ground" aria-hidden="true" />
      <svg className="commerce-scene-paths" viewBox="0 0 520 440" width="520" height="440" aria-hidden="true" preserveAspectRatio="none">
        <defs><marker id={`${prefix}-arrow`} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto"><path d="M0 0 10 5 0 10Z" fill="currentColor" /></marker></defs>
        {hubPaths.map(path => <path key={path} className="scene-hub-path" d={path} pathLength="1" />)}
        {flowPaths.map(path => <path key={path} className="scene-flow-path" d={path} markerEnd={`url(#${prefix}-arrow)`} pathLength="1" />)}
      </svg>
      <div className="commerce-phone-position" aria-hidden="true"><div className="commerce-phone"><div className="commerce-phone-camera" /><div className="commerce-phone-screen"><BrandMark /><strong>ZayloPe</strong><span>Shop. Share. Earn.</span><div className="commerce-phone-route"><i /><i /><i /></div><small>Connected commerce</small></div></div></div>
      {affiliateJourney.map((item,index)=><div key={item.icon} className={`commerce-scene-object commerce-scene-object-${index}`} style={{'--object-index':index}}><span className="commerce-object-depth"><CommerceObject kind={item.icon} /></span><span className="commerce-object-label">{labels[index]}</span></div>)}
    </div>
    <figcaption id={`${prefix}-caption`}><span>Connected commerce within the Zaylo ecosystem</span></figcaption>
  </figure>;
}

