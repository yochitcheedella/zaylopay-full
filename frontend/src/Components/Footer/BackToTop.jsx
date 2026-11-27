import { useEffect, useState } from 'react';
import { ArrowUp } from 'lucide-react';
import { scrollToPageTop } from '../PublicLayout/navigationScroll';
export default function BackToTop() {
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    const update = () => setVisible(window.scrollY > Math.max(640, innerHeight));
    update(); window.addEventListener('scroll', update, {passive:true});
    return () => window.removeEventListener('scroll', update);
  }, []);
  return <button type="button" className="footer-back-top" style={{visibility:visible?'visible':'hidden'}} tabIndex={visible?0:-1} aria-label="Back to top" onClick={() => {
    scrollToPageTop(); const heading = document.querySelector('main h1');
    if (heading) {heading.tabIndex=-1; heading.focus({preventScroll:true});}
  }}><ArrowUp size={20} aria-hidden="true" /></button>;
}
