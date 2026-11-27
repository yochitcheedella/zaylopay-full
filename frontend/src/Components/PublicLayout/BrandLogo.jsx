import { Link } from 'react-router-dom';

// One rendering of the established public brand mark, including inside illustrations.
export function BrandMark(props) {
  return <svg viewBox="0 0 32 32" aria-hidden="true" focusable="false" {...props}><rect width="32" height="32" rx="8" fill="var(--zp-emerald)" /><path fill="white" d="M8 5h16v3.3L13.7 20.6H24V24H7.7v-3.3L18 8.5H8z" /></svg>;
}
export default function BrandLogo({ onClick }) {
  return <Link to="/" className="brand" aria-label="ZayloPe home" onClick={onClick}><BrandMark className="brand-mark" /><span>Zaylo<span className="brand-green">Pe</span></span></Link>;
}
