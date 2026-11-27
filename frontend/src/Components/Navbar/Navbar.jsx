import './Navbar.css';
import BrandLogo from '../PublicLayout/BrandLogo';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useEffect, useRef, useState } from 'react';

const navigation = [['/', 'Home'], ['/about', 'About Us'], ['/how-it-works', 'How It Works'], ['/membership', 'Membership']];

export default function Navbar() {
  const [openLocation, setOpenLocation] = useState(null);
  const [scrolled, setScrolled] = useState(false);
  const navbar = useRef(null);
  const toggle = useRef(null);
  const panel = useRef(null);
  const { key } = useLocation();
  const open = openLocation === key;
  const closeMenu = () => setOpenLocation(null);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => {
    const desktop = window.matchMedia('(min-width: 960px)');
    const onResize = () => { if (desktop.matches) setOpenLocation(null); };
    desktop.addEventListener('change', onResize);
    return () => desktop.removeEventListener('change', onResize);
  }, []);

  useEffect(() => {
    if (!open) {
      document.body.style.overflow = '';
      return;
    }
    document.body.style.overflow = 'hidden';
    // Wait for the opening panel to become visible before moving keyboard focus.
    let focusFrame;
    const openingFrame = requestAnimationFrame(() => {
      focusFrame = requestAnimationFrame(() => panel.current?.querySelector('a')?.focus());
    });
    const onOutside = event => {
      if (!navbar.current?.contains(event.target)) setOpenLocation(null);
    };
    document.addEventListener('pointerdown', onOutside);
    document.addEventListener('focusin', onOutside);
    return () => {
      document.body.style.overflow = '';
      cancelAnimationFrame(openingFrame);
      cancelAnimationFrame(focusFrame);
      document.removeEventListener('pointerdown', onOutside);
      document.removeEventListener('focusin', onOutside);
    };
  }, [open]);

  return (
    <nav ref={navbar} className={`navbar ${scrolled ? 'navbar-scrolled' : ''}`} aria-label="Primary navigation"
      onKeyDown={event => {
        if (event.key === 'Escape' && open) { closeMenu(); toggle.current?.focus(); }
      }}>
      <div className="navbar-inner">
        <BrandLogo onClick={closeMenu} />
        <button ref={toggle} type="button" className="nav-toggle" onClick={() => setOpenLocation(open ? null : key)} aria-expanded={open} aria-controls="primary-navigation">
          <span className="sr-only">{open ? 'Close navigation' : 'Open navigation'}</span><span /><span /><span />
        </button>
        <div className={`nav-panel ${open ? 'nav-open' : ''}`} id="primary-navigation" ref={panel}>
          <div className="nav-panel-content">
            <div className="nav-links">
              {navigation.map(([to, label]) => <NavLink key={to} to={to} end={to === '/'} onClick={closeMenu}>{label}</NavLink>)}
            </div>
            <div className="nav-actions">
              <Link className="register-nav-btn" to="/register" onClick={closeMenu}>
                Get Started
                <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M7 17 17 7M7 7h10v10" /></svg>
              </Link>
              <Link className="login-nav-btn" to="/login" onClick={closeMenu}>Login</Link>
            </div>
          </div>
        </div>
      </div>
    </nav>
  );
}




