import { configuredSocialUrl } from './footerConfig';
const icons = {
  'instagram': <><rect x="3" y="3" width="18" height="18" rx="5" fill="none" stroke="currentColor" strokeWidth="2"/><circle cx="12" cy="12" r="4" fill="none" stroke="currentColor" strokeWidth="2"/><circle cx="17.5" cy="6.5" r="1"/></>,
  'facebook': <><path d="M14 22v-9h3l.5-4H14V7c0-1 .3-2 2-2h2V1h-3c-4 0-6 2-6 6v2H6v4h3v9Z"/></>,
  'youtube': <><path d="M22 7s-.2-2-2-2.5C18 4 12 4 12 4s-6 0-8 .5C2.2 5 2 7 2 7a34 34 0 0 0 0 10s.2 2 2 2.5C6 20 12 20 12 20s6 0 8-.5c1.8-.5 2-2.5 2-2.5a34 34 0 0 0 0-10Z"/><path d="m10 8 6 4-6 4Z" fill="white"/></>,
  'linkedin': <><path d="M3 8h4v13H3Zm2-6a2 2 0 1 1 0 4 2 2 0 0 1 0-4Zm5 6h4v2c1-2 7-3 7 4v7h-4v-7c0-3-3-3-3 0v7h-4Z"/></>,
  'x': <><path d="M18.9 2H22l-6.8 7.8L23.2 22H17l-4.9-7.4L5.7 22H2.5l7.9-9L.8 2h6.4l4.4 6.7L18.9 2Zm-1.1 18h1.7L6.2 4H4.4Z"/></>,
};
export default function SocialMediaLinks({platforms}) {
  return <div className="footer-socials" aria-label="Social media">{platforms.map(platform => {
    const url = configuredSocialUrl(platform);
    const icon = <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">{icons[platform.icon]}</svg>;
    return url ? <a key={platform.id} href={url} target="_blank" rel="noopener noreferrer" aria-label={`${platform.name} (opens in a new tab)`}>{icon}</a>
      : <span key={platform.id} role="img" aria-label={`${platform.name} — link not yet available`}>{icon}</span>;
  })}</div>;
}
