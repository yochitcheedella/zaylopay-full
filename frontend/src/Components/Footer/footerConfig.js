// Profile URLs stay unset until official destinations are supplied.
export const socialPlatforms = [
  {id:'instagram', name:'Instagram', icon:'instagram', url:null, enabled:false, hosts:['instagram.com','www.instagram.com']},
  {id:'facebook', name:'Facebook', icon:'facebook', url:null, enabled:false, hosts:['facebook.com','www.facebook.com']},
  {id:'youtube', name:'YouTube', icon:'youtube', url:null, enabled:false, hosts:['youtube.com','www.youtube.com','youtu.be']},
  {id:'linkedin', name:'LinkedIn', icon:'linkedin', url:null, enabled:false, hosts:['linkedin.com','www.linkedin.com']},
  {id:'x', name:'X (Twitter)', icon:'x', url:null, enabled:false, hosts:['x.com','www.x.com','twitter.com','www.twitter.com']},
];
export const quickLinks = [{label:'Home',to:'/'},{label:'About Us',to:'/about'},{label:'How It Works',to:'/how-it-works'},{label:'Membership',to:'/membership'}];
export const supportLinks = [{label:'Membership FAQs',to:'/membership#membership-faq'}];
// Add only approved, implemented destinations. Do not link to placeholder pages.
export const legalLinks = [];
export const supportContact = {email:'support@zaylope.com',phone:'+91 87546 49852',telephone:'+918754649852'};
export const earningsDisclaimer = {
  // Enable only after recorded management approval. Defaults off in every build.
  approved: import.meta.env?.VITE_FOOTER_EARNINGS_DISCLAIMER_APPROVED === 'true',
  text: 'Affiliate earnings depend on qualifying purchases and applicable commission terms. Earnings are not guaranteed.',
};

export function configuredSocialUrl(platform) {
  if (!platform.enabled || !platform.url) return null;
  try {
    const url = new URL(platform.url);
    if (url.protocol !== 'https:' || url.username || url.password || url.port || url.pathname === '/' || !platform.hosts.includes(url.hostname.toLowerCase())) return null;
    return url.href;
  } catch { return null; }
}
