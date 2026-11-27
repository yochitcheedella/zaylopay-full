import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { useViewportObserver } from '../../motion';

import Icon from '../Home/Icon';
import './About.css';
import CommerceHeroScene from './CommerceHeroScene';
import WhoWeAre from './WhoWeAre';
import WhyWeExist from './WhyWeExist';
import ConnectedEcosystem from './ConnectedEcosystem';
import WhatMakesDifferent from './WhatMakesDifferent';
import PeoplePossibilities from './PeoplePossibilities';
import TrustCommitment from './TrustCommitment';
import ClarityFaq from './ClarityFaq';
import FinalAboutCta from './FinalAboutCta';
import './AboutRhythm.css';

function Actions({ light = false }) {
  return (
    <div className={`about-actions ${light ? 'about-actions-light' : ''}`}>
      <Link className="about-button about-button-primary" to="/membership">
        Explore membership <Icon name="arrow" />
      </Link>
      <Link className="about-button about-button-secondary" to="/how-it-works">
        How it works <Icon name="arrow" />
      </Link>
    </div>
  );
}

function About() {
  const main = useRef(null);
  useViewportObserver(main, {
    selector: '[data-about-reveal]',
    enterClass: 'about-entered',
    activeClass: 'in-view',
    threshold: 0.08,
    rootMargin: '0px 0px -30px 0px',
    once: true,
  });

  return (
    <div className="about-page">
      <main id="about-main" ref={main} tabIndex="-1">
        <section className="about-hero about-container" aria-labelledby="about-title" data-about-reveal>
          <div className="about-hero-copy">
            <p className="about-eyebrow">About ZayloPe</p>
            <h1 id="about-title">
              <span className="about-hero-line">Good finds.</span>
              <span className="about-hero-line">Greater opportunities.</span>
            </h1>
            <p className="about-lead">
              We’re building a connection between everyday shopping, product sharing and eligible affiliate opportunities within the Zaylo ecosystem.
            </p>
            <Actions />
            <p className="about-fineprint">
              Shop. Share. Earn. <span>With eligibility at every step.</span>
            </p>
          </div>
          <CommerceHeroScene />
        </section>

        <WhoWeAre />
        <WhyWeExist />
        <ConnectedEcosystem />
        <WhatMakesDifferent />
        <PeoplePossibilities />
        <TrustCommitment />
        <ClarityFaq />
        <FinalAboutCta />
      </main>
    </div>
  );
}

export default About;
