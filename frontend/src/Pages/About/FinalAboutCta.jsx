import { Link } from 'react-router-dom';
import Icon from '../Home/Icon';
import commerceArt from '../../assets/about-final-commerce.webp';
import './FinalAboutCta.css';

export default function FinalAboutCta() {
  return <section className="about-container final-cta-section" id="your-next-chapter" aria-labelledby="about-final-title" data-about-reveal>
    <div className="final-cta-panel">
      <div className="final-cta-copy">
        <p className="about-eyebrow">Your Next Chapter</p>
        <h2 id="about-final-title">Your journey starts with a good find.</h2>
        <p className="final-cta-description">Explore how shopping and sharing can connect with affiliate opportunities. Start by understanding the membership.</p>
        <div className="final-cta-actions"><Link className="final-cta-button final-cta-primary" to="/membership">Explore membership<Icon name="arrow"/></Link><Link className="final-cta-button final-cta-secondary" to="/how-it-works">How it works<Icon name="arrow"/></Link></div>
        <p className="final-cta-disclaimer">Membership fees and eligibility conditions apply. Earnings are not guaranteed.</p>
      </div>
      <figure className="final-cta-visual"><img src={commerceArt} width="1200" height="900" loading="lazy" decoding="async" alt="ZayloPe commerce illustration with a smartphone, product headphones, shopping bags, referral link, sharing network, qualifying-order shield and rupee wallet."/></figure>
    </div>
  </section>;
}

