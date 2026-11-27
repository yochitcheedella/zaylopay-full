import { Component, useEffect } from 'react';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { PageTransition } from '../../motion';
import Navbar from '../Navbar/Navbar';
import Footer from '../Footer/Footer';
import './PublicLayout.css';

export function PageLoading() {
  return <div className="public-state" role="status"><span className="public-loading" aria-hidden="true" />Loading page…</div>;
}
export class PageErrorBoundary extends Component {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  render() {
    if (this.state.failed) return <main className="public-state"><h1>This page couldn’t load.</h1><p>Please reload to try again. Your saved session has not been cleared.</p><button className="zp-button zp-primary" onClick={() => window.location.reload()}>Reload page</button><a href="/" className="zp-button zp-secondary">Return home</a></main>;
    return this.props.children;
  }
}
export function NotFound() {
  return <main className="public-state"><p className="zp-eyebrow">404 / Page Not Found</p><h1>Let’s find your next step.</h1><p>This address doesn’t match an available page.</p><Link className="zp-button zp-primary" to="/">Return home</Link><Link className="zp-button zp-secondary" to="/membership">Explore membership</Link></main>;
}
export default function PublicLayout() {
  const { pathname } = useLocation();
  return <div className="public-layout"><a className="public-skip" href="#public-content">Skip to content</a><Navbar /><div id="public-content" tabIndex="-1"><PageErrorBoundary key={pathname}><PageTransition routeKey={pathname}><Outlet /></PageTransition></PageErrorBoundary></div><Footer /></div>;
}
const metadata = {
  '/': ['ZayloPe | Shop. Share. Earn.', 'Explore ZayloPe membership information, planned shopping benefits and eligible affiliate opportunities within the Zaylo ecosystem.'],
  '/about': ['About ZayloPe | Connected Commerce and Affiliate Opportunities', 'Learn about ZayloPe’s purpose, planned connected commerce experience and responsible approach to eligible affiliate opportunities.'],
  '/how-it-works': ['How ZayloPe Works | The Affiliate Journey', 'Understand the planned journey from eligible product discovery and personal sharing links to qualifying orders and approved commissions.'],
  '/membership': ['ZayloPe Membership | Benefits and Eligibility', 'Review existing ZayloPe membership pricing, proposed benefits, eligibility conditions and the registration steps available today.'],
  '/register': ['Register | ZayloPe', 'Start or resume your ZayloPe application through the existing registration and verification journey.'],
  '/login': ['Log in | ZayloPe', 'Access the existing ZayloPe login experience.'],
  '/dashboard': ['Member Dashboard | ZayloPe', 'Your ZayloPe member workspace.'],
};
export function RouteMetadata() {
  const { pathname } = useLocation();
  useEffect(() => {
    const [title, description] = metadata[pathname] || ['Page not found | ZayloPe', 'Find your way to an available ZayloPe page.'];
    document.title = title;
    for (const [attribute, name, content] of [['name', 'description', description], ['property', 'og:title', title], ['property', 'og:description', description], ['property', 'og:type', 'website']]) {
      let element = document.head.querySelector(`meta[${attribute}="${name}"]`);
      if (!element) { element = document.createElement('meta'); element.setAttribute(attribute, name); document.head.appendChild(element); }
      element.setAttribute('content', content);
    }
  }, [pathname]);
  return null;
}



