import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { useViewportObserver } from '../../motion';
import Icon from '../Home/Icon';
import commerceArt from '../../assets/about-final-commerce.webp';
import HowProcess from './HowProcess';
import './Howitworkspage.css';

export default function Howitworkspage() {
  const page = useRef(null);
  useViewportObserver(page, {
    selector: '.how-step, .how-eligibility, .how-next',
    enterClass: 'how-entered',
    activeClass: '',
    once: true,
  });
  return <main className="how-page" id="how-main" ref={page}>
    <section className="how-hero" aria-labelledby="how-title">
      <div className="public-container how-hero-grid tw:grid tw:items-center">
        <div className="how-hero-copy">
          <p className="how-kicker">How ZayloPe Works</p>
          <h1 id="how-title">Join. Share. <span>Track. Grow.</span></h1>
          <p className="how-lead">Start with membership, then see how ZayloPe plans to connect your product recommendations with qualifying orders and approved commissions.</p>
          <div className="zp-actions">
            <Link className="zp-button zp-primary" to="#how-process">Explore the steps <Icon name="arrow" /></Link>
            <Link className="zp-button zp-secondary" to="/membership">Explore Membership</Link>
          </div>
        </div>
        <img className="how-hero-art" src={commerceArt} width="1200" height="900" fetchPriority="high"
          alt="A ZayloPe phone surrounded by a product, shopping bags, a referral link and a wallet." />
      </div>
    </section>

    <HowProcess />

    <section className="how-eligibility" aria-labelledby="how-eligibility-title">
      <div className="public-container how-note tw:flex tw:items-start">
        <span className="how-mark"><Icon name="check" /></span>
        <div>
          <h2 id="how-eligibility-title">When does an order qualify?</h2>
          <p>A purchase must be traced to your link and approved under the programme rules. Delivery, cancellations and returns can affect eligibility. Sharing a link or receiving a click earns no commission on its own.</p>
        </div>
      </div>
    </section>

    <section className="how-next" aria-labelledby="how-next-title">
      <div className="public-container how-next-inner tw:flex tw:flex-wrap tw:items-center tw:justify-between">
        <div>
          <h2 id="how-next-title">Explore ZayloPe membership</h2>
          <p>You can review the membership terms and start registration today. Affiliate links, order tracking and commissions are still in development.</p>
        </div>
        <div className="zp-actions">
          <Link className="zp-button zp-lime" to="/membership">Explore Membership <Icon name="arrow" /></Link>
          <Link className="zp-button how-register" to="/register">Get Started</Link>
        </div>
      </div>
    </section>
  </main>;
}
