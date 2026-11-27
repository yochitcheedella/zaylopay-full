import { useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import Icon from '../Home/Icon';
import { aboutFaqs } from './aboutContent';
import './ClarityFaq.css';

export function FaqAccordion({ items }) {
  const [expanded,setExpanded] = useState(null);
  const buttons = useRef([]);
  const prefix = useId();
  return <div className="clarity-list">{items.map((item,index)=>{
    const open=expanded===index;
    return <article className={`clarity-item ${open?'is-open':''}`} key={item.question}>
      <h3><button type="button" id={`${prefix}-question-${index}`} ref={el=>{buttons.current[index]=el;}} aria-expanded={open} aria-controls={`${prefix}-answer-${index}`} onClick={()=>setExpanded(previous=>previous===index?null:index)} onKeyDown={event=>{
        let target;
        if(event.key==='ArrowDown')target=(index+1)%items.length;
        else if(event.key==='ArrowUp')target=(index-1+items.length)%items.length;
        else if(event.key==='Home')target=0;
        else if(event.key==='End')target=items.length-1;
        if(target!==undefined){
          event.preventDefault();
          const next = buttons.current[target];
          next?.focus({preventScroll:true});
          next?.scrollIntoView({block:'nearest',inline:'nearest'});
        }
      }}><span>{item.question}</span><span className="clarity-indicator" aria-hidden="true">{open?'−':'+'}</span></button></h3>
      <div className="clarity-panel" id={`${prefix}-answer-${index}`} role="region" aria-labelledby={`${prefix}-question-${index}`} aria-hidden={!open} inert={!open}>
        <div className="clarity-panel-inner"><div className="clarity-answer"><p>{item.answer}</p>{item.link&&<Link to={item.link[0]} className="clarity-answer-link">{item.link[1]}<Icon name="arrow"/></Link>}</div></div>
      </div>
    </article>;
  })}</div>;
}

export function AboutFaq() {
  return <FaqAccordion items={aboutFaqs} />;
}

export default function ClarityFaq() {
  return <section className="clarity-band" id="about-faq" aria-labelledby="about-faq-title">
    <div className="about-container clarity-section" data-about-reveal>
      <div className="clarity-intro"><p className="about-eyebrow">07 / A Little More Clarity</p><h2 id="about-faq-title">Good questions.<br/><span>Clearer next steps.</span></h2><p>Understand the essentials before you decide where to go next.</p><Link className="clarity-membership" to="/membership">Explore membership<Icon name="arrow"/></Link><div className="clarity-more"><p>Looking for membership-specific details?</p><Link to="/membership#membership-faq">Read the membership FAQs<Icon name="arrow"/></Link></div></div>
      <AboutFaq/>
    </div>
  </section>;
}
