import { useEffect, useId, useRef } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../Home/Icon';
import './WhoWeAre.css';

// Original vector illustrations. These are conceptual people/products, not staff portraits or 3D renders.
function WhoArtwork({ kind }) {
  const id = useId().replaceAll(':', '');
  const descriptions = {
    community: 'Three people connected through thoughtful product recommendations.',
    discover: 'A shopping bag and product tag illustrate eligible product discovery.',
    share: 'A personal link connects two people sharing a recommendation.',
    possibilities: 'A wallet, rupee token and verification check illustrate eligibility before commission.',
  };
  return <svg className={`who-artwork who-artwork-${kind}`} viewBox={kind === 'community' ? '0 0 440 260' : '0 0 240 170'} role="img" aria-label={descriptions[kind]}>
    <defs><linearGradient id={`${id}-green`} x1="20%" y1="0" x2="90%" y2="100%"><stop stopColor="#42ac84"/><stop offset="1" stopColor="var(--zp-forest)"/></linearGradient><linearGradient id={`${id}-mint`} x1="10%" y1="0" x2="90%" y2="100%"><stop stopColor="#f0f8e9"/><stop offset="1" stopColor="#c1dbbd"/></linearGradient></defs>
    {kind === 'community' && <>
      <ellipse cx="220" cy="229" rx="156" ry="15" fill="var(--zp-forest)" opacity=".08"/>
      <path d="m52 183 171-40 165 40-164 55Z" fill="#c8ddc6"/><path d="m52 175 171-40 165 40-164 55Z" fill={`url(#${id}-mint)`} stroke="#aac8ad"/>
      <path className="who-sharing-path" d="m123 161 97 35 100-38" fill="none" stroke="var(--zp-accent-text)" strokeWidth="2" strokeDasharray="4 6"/>
      <g className="who-person who-person-left"><path d="M84 167v-32a34 34 0 0 1 68 0v32l-34 12Z" fill={`url(#${id}-green)`}/><path d="m104 166 13 25 20-8" fill="none" stroke="var(--zp-forest)" strokeWidth="11" strokeLinecap="round"/><circle cx="118" cy="86" r="23" fill="#b8ceb0"/><path d="M95 83a23 23 0 0 1 46-2l-11-3-7-11-8 10Z" fill="var(--zp-forest)"/></g>
      <g className="who-person who-person-right"><path d="M285 160v-31a34 34 0 0 1 68 0v31l-34 12Z" fill="#91b18d"/><circle cx="319" cy="80" r="23" fill="#dce8cf"/><path d="M297 77a22 22 0 0 1 44-1l-13-8-15 5-8-3Z" fill="var(--zp-forest)"/><path d="m304 164-13 20-16-4" fill="none" stroke="#70956f" strokeWidth="10" strokeLinecap="round"/></g>
      <g className="who-person who-person-center"><path d="M178 207v-45a41 41 0 0 1 82 0v45l-41 14Z" fill="var(--zp-forest)"/><circle cx="219" cy="111" r="27" fill="#c9d9b9"/><path d="M193 109a27 27 0 0 1 52-9l-15-3-8-12-11 14Z" fill="#315d43"/><path d="m211 147 8 11 9-11" fill="none" stroke="#d4f578" strokeWidth="2"/></g>
      <g transform="translate(261 48) rotate(7)"><rect width="62" height="42" rx="10" fill="white" stroke="#a9c6b1"/><path d="m10 42 2 10 12-10" fill="white" stroke="#a9c6b1"/><path d="M16 16h30M16 25h20" stroke="var(--zp-accent-text)" strokeWidth="3" strokeLinecap="round"/></g>
      <g transform="translate(146 29) rotate(-9)"><rect width="52" height="48" rx="10" fill="white" stroke="#a9c6b1"/><path d="M18 18h17l2 21H16l2-21Zm4 0v-5a5 5 0 0 1 10 0v5" fill="none" stroke="var(--zp-accent-text)" strokeWidth="2"/></g>
    </>}
    {kind === 'discover' && <>
      <ellipse cx="126" cy="149" rx="66" ry="9" fill="#021e18" opacity=".22"/>
      <path d="m73 53 82-10 16 16-1 80-82 11-16-14Z" fill="#315c43"/><path d="m73 53 82-10 2 91-80 11Z" fill={`url(#${id}-mint)`}/><path d="m155 43 16 16-1 80-13-5Z" fill="#8cab83"/>
      <path d="M96 59V35c0-25 36-28 38-4l2 23" fill="none" stroke="#e4f3d7" strokeWidth="7" strokeLinecap="round"/>
      <path d="M102 88h28l-26 27h30" fill="none" stroke="var(--zp-forest)" strokeWidth="5" strokeLinejoin="round"/>
      <g transform="translate(161 79) rotate(12)"><path d="M0 5 14 0h28v48H0Z" fill="#d4f578"/><circle cx="11" cy="12" r="3" fill="var(--zp-forest)"/><path d="m11 28 6 6 13-16" fill="none" stroke="var(--zp-forest)" strokeWidth="3"/></g>
    </>}
    {kind === 'share' && <>
      <ellipse cx="120" cy="149" rx="83" ry="8" fill="var(--zp-forest)" opacity=".07"/>
      <path className="who-sharing-path" d="M46 98h45m58-33h43m-72 37v30" stroke="var(--zp-accent-text)" strokeWidth="2" strokeDasharray="4 5" fill="none"/>
      <g fill="white" stroke="#a4bfa7"><rect x="14" y="73" width="53" height="57" rx="15"/><rect x="173" y="39" width="53" height="57" rx="15"/></g>
      <g fill="var(--zp-forest)"><circle cx="40" cy="91" r="7"/><path d="M27 116a13 13 0 0 1 26 0Z"/><circle cx="199" cy="57" r="7"/><path d="M186 82a13 13 0 0 1 26 0Z"/></g>
      <g transform="translate(86 43) rotate(-8 34 34)"><rect x="4" y="6" width="68" height="68" rx="18" fill="#85a184"/><rect width="68" height="68" rx="18" fill="var(--zp-forest)"/><path d="m29 28 7-7a9 9 0 0 1 13 13l-7 7m-5-1-7 7a9 9 0 0 1-13-13l7-7m2 12 14-14" fill="none" stroke="#d4f578" strokeWidth="4" strokeLinecap="round"/></g>
    </>}
    {kind === 'possibilities' && <>
      <ellipse cx="121" cy="149" rx="83" ry="9" fill="var(--zp-forest)" opacity=".08"/>
      <path d="M39 65 144 50l13 13v77H48a12 12 0 0 1-12-12V76Z" fill="var(--zp-forest)"/><path d="m36 64 102-13 6 77-95 12a12 12 0 0 1-13-12Z" fill={`url(#${id}-green)`}/><path d="M125 82h38v31h-38a15 15 0 0 1 0-31Z" fill="#0a513c"/><circle cx="139" cy="97" r="4" fill="#d4f578"/>
      <circle cx="90" cy="44" r="27" fill="#91ab60"/><circle cx="86" cy="39" r="27" fill={`url(#${id}-mint)`} stroke="#e9f6dc" strokeWidth="2"/><text x="86" y="49" textAnchor="middle" fill="var(--zp-forest)" fontSize="30" fontWeight="700">₹</text>
      <g className="who-verified-mark"><path d="m180 73 27 10v23c0 22-27 34-27 34s-27-12-27-34V83Z" fill="#c7dfb8" stroke="#9dbb8c"/><path d="m167 105 9 9 17-23" fill="none" stroke="var(--zp-forest)" strokeWidth="4" strokeLinecap="round" strokeLinejoin="round"/></g>
    </>}
  </svg>;
}

function BentoCard({ kind, index, children }) {
  const card = useRef(null);
  const pointer = useRef({ frame: null, x: 0, y: 0 });
  const reset = () => {
    cancelAnimationFrame(pointer.current.frame); pointer.current.frame = null;
    card.current?.style.setProperty('--who-x', '0deg'); card.current?.style.setProperty('--who-y', '0deg');
  };
  useEffect(() => {
    const state = pointer.current;
    const media = matchMedia('(prefers-reduced-motion: reduce)');
    media.addEventListener('change', reset);
    return () => { cancelAnimationFrame(state.frame); media.removeEventListener('change', reset); };
  }, []);
  const move = event => {
    if (event.pointerType !== 'mouse' || !matchMedia('(min-width: 801px) and (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)').matches) return;
    const rect = event.currentTarget.getBoundingClientRect();
    pointer.current.x = Math.max(-1,Math.min(1,(event.clientX-rect.left)/rect.width*2-1));
    pointer.current.y = Math.max(-1,Math.min(1,(event.clientY-rect.top)/rect.height*2-1));
    if (pointer.current.frame) return;
    pointer.current.frame = requestAnimationFrame(() => { card.current?.style.setProperty('--who-x', `${-pointer.current.y*2}deg`); card.current?.style.setProperty('--who-y', `${pointer.current.x*2}deg`); pointer.current.frame = null; });
  };
  return <div className={`who-card-reveal who-card-position-${kind}`} style={{'--who-index':index}}><article ref={card} className={`who-card who-card-${kind}`} onPointerMove={move} onPointerLeave={reset}>{children}</article></div>;
}

export default function WhoWeAre() {
  return <section className="about-intro-band who-section" aria-labelledby="about-story-title">
    <div className="about-container who-shell" data-about-reveal>
      <header className="who-heading"><div><p className="about-eyebrow">01 / Who We Are</p><h2 id="about-story-title">Built around people.<br /><span>Connected by commerce.</span></h2></div><p className="who-description">We’re building ZayloPe to connect everyday shopping, meaningful recommendations and eligible affiliate opportunities within the Zaylo ecosystem.</p></header>
      <div className="who-grid">
        <BentoCard kind="community" index={0}><WhoArtwork kind="community"/><div className="who-card-copy"><p className="who-card-label">People, first</p><h3>People at the center</h3><p>Turning everyday product recommendations into meaningful connections.</p></div></BentoCard>
        <BentoCard kind="discover" index={1}><div className="who-card-copy"><p className="who-card-label">A useful find</p><h3>Discover better</h3><p>Explore the idea of eligible product discovery within Zaylo.</p></div><WhoArtwork kind="discover"/></BentoCard>
        <BentoCard kind="share" index={2}><div className="who-card-copy"><p className="who-card-label">A personal connection</p><h3>Share with purpose</h3><p>Thoughtful recommendations. Personal links. Your community.</p></div><WhoArtwork kind="share"/></BentoCard>
        <BentoCard kind="possibilities" index={3}><WhoArtwork kind="possibilities"/><div className="who-card-copy"><p className="who-card-label">Eligibility comes first</p><h3>More possibilities</h3><p>A qualifying purchase may lead to an eligible commission under approved programme rules. Earnings are not guaranteed.</p></div><Link className="about-text-link" to="/how-it-works">Explore how it works <Icon name="arrow"/></Link></BentoCard>
      </div>
    </div>
  </section>;
}
