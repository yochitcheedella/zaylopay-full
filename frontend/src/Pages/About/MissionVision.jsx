import Icon from '../Home/Icon';
import missionArt from '../../assets/mission-purpose.webp';
import visionArt from '../../assets/vision-connected.webp';
import './MissionVision.css';

// No approved statements or publication authorization exist yet. Keep the
// proposed copy out of every production build.
export default function MissionVision() {
  if (!import.meta.env.DEV) return null;

  return <section className="about-container direction-section" aria-labelledby="about-direction-title" data-about-reveal data-about-surface="warm">
    <header className="direction-heading">
      <p className="about-eyebrow">03 / Our Direction</p>
      <h2 id="about-direction-title">Driven by purpose.<br/><span>Inspired by possibilities.</span></h2>
      <p>Building a more connected, transparent and accessible commerce ecosystem.</p>
    </header>
    <div className="direction-grid">
      <div className="direction-reveal"><article className="direction-card direction-mission">
        <div className="direction-copy">
        <p className="direction-label">Our mission</p>
        <h3>Make meaningful connections possible.</h3>
        <p className="direction-description">Connect people with eligible Zaylo products through accessible sharing, clear referral attribution and transparent affiliate opportunities.</p>
        </div>
        <img className="direction-art" src={missionArt} alt="A target and arrow beside a shopping bag and parcel represent purposeful commerce connections." width="768" height="768" loading="lazy" decoding="async"/>
      </article></div>
      <div className="direction-reveal"><article className="direction-card direction-vision">
        <div className="direction-copy">
        <p className="direction-label">Our vision</p>
        <h3>Build a more connected commerce future.</h3>
        <p className="direction-description">Create a shopping ecosystem where people, products and communities grow through responsible recommendations and meaningful participation.</p>
        </div>
        <img className="direction-art" src={visionArt} alt="Community figures connected across a globe represent a shared commerce future." width="768" height="768" loading="lazy" decoding="async"/>
      </article></div>
    </div>
    <ul className="direction-values" aria-label="Our guiding values">
      <li><Icon name="user"/>People first.</li><li><Icon name="check"/>Transparency.</li><li><Icon name="share"/>Responsible growth.</li>
    </ul>
    <p className="direction-preview-note">Development preview · Proposed statements awaiting approval. This section is excluded from production.</p>
  </section>;
}
