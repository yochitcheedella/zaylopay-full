import { useId } from 'react';
import Icon from '../Home/Icon';
import './WhyWeExist.css';

function ConnectionStory({ connected }) {
  const id = useId().replaceAll(':','');
  const nodes = connected ? [{x:28,y:58,icon:'share'},{x:156,y:58,icon:'link'},{x:284,y:58,icon:'check'},{x:412,y:58,icon:'reward'}] : [{x:42,y:34,icon:'bag'},{x:218,y:79,icon:'share'},{x:394,y:22,icon:'link'}];
  return <svg className="why-story-art" viewBox="0 0 520 220" width="520" height="220" role="img" aria-label={connected ? 'A recommendation connects through a personal referral link to a qualifying purchase and eligible commission.' : 'Shopping, recommendations and referral links remain separated, with gaps between their connections.'}>
    <defs><linearGradient id={`${id}-surface`} x1="0" y1="0" x2="1" y2="1"><stop stopColor={connected?'#f3fae9':'#ffffff'}/><stop offset="1" stopColor="#cee2c8"/></linearGradient><marker id={`${id}-arrow`} viewBox="0 0 10 10" refX="8" refY="5" markerWidth="5" markerHeight="5" orient="auto"><path d="M0 0 10 5 0 10Z" fill={connected?'#d4f578':'#90ad97'}/></marker></defs>
    {connected ? <g className="why-progress-paths" fill="none" stroke="#d4f578" strokeWidth="2" markerEnd={`url(#${id}-arrow)`}>{[108,236,364].map((x,index)=><path key={x} d={`M${x} 103h40`} pathLength="1" style={{'--path-index':index}}/>)}</g> : <g fill="none" stroke="#a7b9ac" strokeWidth="2" strokeDasharray="4 7"><path d="m126 90 39 11m22 7 23 6m91-4 31-11m24-7 29-10"/></g>}
    {nodes.map((node,index)=><g key={node.icon} className={`why-commerce-tile why-commerce-tile-${index}`} style={{'--tile-index':index}} transform={connected?'':`rotate(${index===0?-8:index===1?5:-4} ${node.x+40} ${node.y+44})`}>
      <ellipse cx={node.x+44} cy={node.y+103} rx="45" ry="8" fill={connected?'#021e18':'#083d31'} opacity={connected?'.25':'.07'}/>
      <rect x={node.x+4} y={node.y+7} width="80" height="88" rx="17" fill={connected?'#659a77':'#b3c9b5'}/>
      <rect x={node.x} y={node.y} width="80" height="88" rx="17" fill={`url(#${id}-surface)`} stroke={connected?'#e4f3cf':'#d3e2d5'}/>
      {connected && node.icon === 'check' && <path d={`M${node.x+40} ${node.y+11}l24 9v18c0 18-24 31-24 31s-24-13-24-31V${node.y+20}Z`} fill="#c1d9b4" stroke="#90b084"/>}
      <Icon name={node.icon} className="why-art-symbol" x={node.x+20} y={node.y+23} width="40" height="40" strokeWidth="1.6"/>
    </g>)}
    {!connected && <g stroke="#96ad9e" strokeWidth="2" strokeLinecap="round"><path d="m167 86 8 11m-6 9 10-5m168-13 9 8m-6-17 5-5"/></g>}
  </svg>;
}

export default function WhyWeExist() {
  return <section className="about-container about-section why-exist" aria-labelledby="about-reason-title" data-about-reveal data-about-surface="warm">
    <header className="why-heading"><p className="about-eyebrow">02 / Why We Exist</p><h2 id="about-reason-title">Every recommendation<br /><span>holds a possibility.</span></h2><p>We’re building a more connected way to discover, share and participate in everyday commerce.</p></header>
    <div className="why-story-grid">
      <div className="why-card-reveal"><article className="why-story-card why-disconnect"><p className="why-card-label">The disconnect</p><h3>Good finds get shared.</h3><p className="why-card-description">Everyday recommendations create value, but the connection between sharing and purchasing is not always visible.</p><div className="why-visual"><ConnectionStory connected={false}/><p className="why-art-note">A useful find. A recommendation. A missing connection.</p></div></article></div>
      <div className="why-card-reveal"><article className="why-story-card why-approach"><p className="why-card-label">The ZayloPe approach</p><h3>Connections create possibilities.</h3><p className="why-card-description">Our intended approach uses personal referral links to connect eligible products, communities and qualifying purchases within Zaylo.</p><div className="why-visual"><ConnectionStory connected/><ol className="why-flow-labels"><li>Recommend</li><li>Connect</li><li>Qualify</li><li>Eligible commission</li></ol></div><p className="about-supporting-note">Meaningful participation. No guaranteed purchases or income.</p></article></div>
    </div>
    <div className="why-value-strip"><ul><li><Icon name="share"/><span>Meaningful sharing</span></li><li><Icon name="link"/><span>Clear attribution</span></li><li><Icon name="check"/><span>Eligible rewards</span></li></ul></div>
  </section>;
}
