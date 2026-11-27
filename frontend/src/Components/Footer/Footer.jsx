import { useEffect, useRef } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Mail, Phone, CreditCard } from 'lucide-react';
import BrandLogo from '../PublicLayout/BrandLogo';
import SocialMediaLinks from './SocialMediaLinks';
import BackToTop from './BackToTop';
import {quickLinks,supportLinks,legalLinks,supportContact,socialPlatforms,earningsDisclaimer} from './footerConfig';
import './Footer.css';
const currentYear = new Date().getFullYear();
function FooterNavigation({title,links,children}) {
  return <nav className="footer-column" aria-label={title}><h2>{title}</h2>{links.map(link=><Link key={link.to} to={link.to}>{link.label}</Link>)}{children}</nav>;
}
export default function Footer() {
  const footer = useRef(null);
  const {pathname} = useLocation();
  useEffect(() => {
    const element=footer.current; element.removeAttribute('data-entered');
    if (!('IntersectionObserver' in window)) return;
    const observer=new IntersectionObserver(entries=>{if(entries.some(entry=>entry.isIntersecting)){element.dataset.entered='true';observer.disconnect();}},{threshold:0.05});
    observer.observe(element);return()=>observer.disconnect();
  },[pathname]);
  return <footer ref={footer} className="zaylope-footer"><div className="footer-inner">
    <div className="footer-container">
      <section className="footer-brand" aria-label="ZayloPe"><BrandLogo /><p className="footer-tagline">Shop. Share. Earn.</p><p>Discover products, share recommendations and unlock affiliate earning opportunities with ZayloPe.</p><SocialMediaLinks platforms={socialPlatforms}/></section>
      <FooterNavigation title="Quick Links" links={quickLinks}/>
      <FooterNavigation title="Help & Support" links={supportLinks}><p>Find answers before your next step.</p></FooterNavigation>
      <section className="footer-column footer-contact"><h2>Get in Touch</h2><a href={`mailto:${supportContact.email}`}><Mail size={18} aria-hidden="true"/><span>{supportContact.email}</span></a><a href={`tel:${supportContact.telephone}`}><Phone size={18} aria-hidden="true"/><span>{supportContact.phone}</span></a><p className="footer-payment"><CreditCard size={18} aria-hidden="true"/>Payments via Razorpay</p></section>
    </div>
    {earningsDisclaimer.approved && <p className="footer-disclaimer">{earningsDisclaimer.text}</p>}
    <div className="footer-bottom"><span>© {currentYear} ZayloPe. All rights reserved.</span>{legalLinks.length>0 && <nav aria-label="Legal">{legalLinks.map(link=><Link key={link.to} to={link.to}>{link.label}</Link>)}</nav>}<BackToTop key={pathname}/></div>
  </div></footer>;
}
