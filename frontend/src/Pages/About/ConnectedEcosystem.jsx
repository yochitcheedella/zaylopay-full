import { Link } from 'react-router-dom';
import Icon from '../Home/Icon';
import { BrandMark } from '../../Components/PublicLayout/BrandLogo';
import shoppingArt from '../../assets/ecosystem-shopping.webp';
import affiliateArt from '../../assets/ecosystem-affiliate.webp';
import { getShoppingDestination } from './shoppingDestination';
import './ConnectedEcosystem.css';

export default function ConnectedEcosystem() {
  const shoppingUrl = getShoppingDestination(import.meta.env.VITE_ZAYLO_SHOP_URL);
  return <section className="eco-band" aria-labelledby="about-ecosystem-title">
    <div className="about-container eco-section" data-about-reveal>
      <header className="eco-heading"><p className="about-eyebrow">03 / The Zaylo &amp; ZayloPe Ecosystem</p><h2 id="about-ecosystem-title">Two platforms.<br/><span>One connected experience.</span></h2><p>Discover products on Zaylo. Share eligible products through ZayloPe.</p></header>
      <div className="eco-cards">
        <div className="eco-card-reveal"><article className="eco-card eco-shopping">
          <header className="eco-card-heading"><div><p className="eco-label">The shopping platform</p><h3>Zaylo</h3></div><Icon name="bag"/></header>
          <div className="eco-card-body"><div className="eco-copy"><p>Shopping and eligible product discovery. The place where the intended journey begins with a useful find.</p><ul className="eco-features">{['Product discovery', 'Shopping', 'Eligible products', 'Customer purchases'].map(text=><li key={text}><Icon name="check"/>{text}</li>)}</ul></div><img className="eco-art" src={shoppingArt} width="768" height="768" loading="lazy" decoding="async" alt="Shopping cart, parcels, a bag and headphones illustrate product discovery and shopping."/></div>
          <footer className="eco-card-footer">{shoppingUrl ? <a className="eco-cta" href={shoppingUrl}>Explore Zaylo <Icon name="arrow"/></a> : <p>Product availability and eligibility apply.</p>}</footer>
        </article></div>
        <div className="eco-card-reveal"><article className="eco-card eco-affiliate">
          <header className="eco-card-heading"><div><p className="eco-label">The affiliate platform</p><h3>ZayloPe</h3></div><BrandMark/></header>
          <div className="eco-card-body"><div className="eco-copy"><p>Personal affiliate sharing, attribution and eligible commissions, subject to approved member access and programme rules.</p><ul className="eco-features">{['Personal affiliate links', 'Product sharing', 'Referral attribution', 'Eligible commissions'].map(text=><li key={text}><Icon name="check"/>{text}</li>)}</ul></div><img className="eco-art" src={affiliateArt} width="768" height="768" loading="lazy" decoding="async" alt="A referral phone, link, community connections and wallet illustrate affiliate sharing."/></div>
          <footer className="eco-card-footer"><Link className="eco-cta" to="/how-it-works">Explore how ZayloPe works <Icon name="arrow"/></Link></footer>
        </article></div>
      </div>
      <div className="eco-disclaimer"><p>The intended relationship. Commerce integration and affiliate features are planned; no live integration is claimed here.</p><p>Sharing alone does not guarantee a purchase or commission. Eligibility and programme approval apply.</p></div>
    </div>
  </section>;
}
