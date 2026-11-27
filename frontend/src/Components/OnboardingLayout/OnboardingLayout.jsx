import './OnboardingLayout.css';
import { Link } from 'react-router-dom';
import BrandLogo from '../PublicLayout/BrandLogo';


export function AuthHeader() {
  return <header className="auth-header"><BrandLogo /><Link to="/login">Log in</Link></header>;
}

export function RegistrationCard({ children, className = '' }) {
  return <section className={`onboarding-card ${className}`}>{children}</section>;
}

export default function OnboardingLayout({ children, className = '', cardClassName = '', labelledBy }) {
  return <main className={`onboarding-main ${className}`} aria-labelledby={labelledBy}>
    <RegistrationCard className={cardClassName}>{children}</RegistrationCard>
  </main>;
}

