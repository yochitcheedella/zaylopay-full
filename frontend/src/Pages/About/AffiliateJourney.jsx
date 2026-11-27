import { useId, useRef } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../Home/Icon';
import { affiliateJourney } from './aboutContent';
import JourneyArtwork from './JourneyArtwork';
import './AffiliateJourney.css';

export default function AffiliateJourney({ active, onSelect }) {
  const tabs = useRef([]);
  const finalLink = useRef(null);
  const prefix = useId();
  const step = affiliateJourney[active];
  const last = active === affiliateJourney.length - 1;
  const select = index => { onSelect(index); tabs.current[index]?.focus({ preventScroll: true }); };
  const next = () => {
    onSelect(active + 1);
    if (active === affiliateJourney.length - 2) requestAnimationFrame(() => finalLink.current?.focus({ preventScroll: true }));
  };
  return <div className="aj-card">
    <div className="aj-tabs" role="tablist" aria-label="Explore the affiliate journey" aria-orientation="horizontal" style={{'--aj-progress': active / (affiliateJourney.length - 1)}}>
      <span className="aj-track" aria-hidden="true"><span/></span>
      {affiliateJourney.map((item,index)=><button key={item.title} id={`${prefix}-tab-${index}`} ref={el=>{tabs.current[index]=el;}} type="button" role="tab" aria-selected={index===active} tabIndex={index===active?0:-1} aria-controls={`${prefix}-panel-${index}`} className={index<active?'aj-complete':''} onClick={()=>onSelect(index)} onKeyDown={event=>{
        const offset={ArrowRight:1,ArrowDown:1,ArrowLeft:-1,ArrowUp:-1};
        if(event.key in offset){event.preventDefault();select((active+offset[event.key]+affiliateJourney.length)%affiliateJourney.length);}
        else if(event.key==='Home'||event.key==='End'){event.preventDefault();select(event.key==='Home'?0:affiliateJourney.length-1);}
      }}><span className="aj-step-mark" aria-hidden="true">{index<active?<Icon name="check"/>:`0${index+1}`}</span><span className="aj-tab-label">{item.short}</span><span className="aj-step-state">{index===active?'Current':index<active?'Earlier':'Upcoming'}</span><span className="sr-only">Step {index+1} of 5</span></button>)}
    </div>
    <p className="aj-mobile-status" aria-hidden="true">Step {active+1} of 5 · {step.short}</p>
    {affiliateJourney.map((item,index)=><div key={item.title} role="tabpanel" id={`${prefix}-panel-${index}`} aria-labelledby={`${prefix}-tab-${index}`} hidden={index!==active} tabIndex="0" className="aj-panel">
      <div className="aj-visual"><JourneyArtwork kind={item.icon}/><p>{item.takeaway}</p></div>
      <div className="aj-detail"><p className="about-eyebrow">Step {index+1} of {affiliateJourney.length}</p><h3>{item.title}</h3><p className="aj-description">{item.description}</p><div className="aj-note"><Icon name="check"/><div><p className="aj-note-label">Know what qualifies</p><p>{item.condition}</p></div></div></div>
    </div>)}
    <div className="aj-controls"><button type="button" className="about-button about-button-secondary" disabled={active===0} onClick={()=>onSelect(active-1)}>Previous</button><span className="aj-position" aria-hidden="true">{active+1} / {affiliateJourney.length}</span>{last?<Link ref={finalLink} className="about-button about-button-primary" to="/how-it-works">Explore how it works <Icon name="arrow"/></Link>:<button type="button" className="about-button about-button-primary" onClick={next}>Next <Icon name="arrow"/></button>}</div>
    <p className="about-supporting-note aj-supporting-note"><strong>An educational walkthrough.</strong><span className="about-supporting-note-copy"> This component explains the planned model. It does not generate links, attribute orders, calculate earnings or create backend transactions.</span></p>
    <p className="sr-only" role="status">Step {active+1} of {affiliateJourney.length}: {step.title}</p>
  </div>;
}
