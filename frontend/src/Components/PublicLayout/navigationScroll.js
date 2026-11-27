export function scrollToPageTop() {
  window.dispatchEvent(new Event('zaylope:cancel-restoration'));
  window.scrollTo({ top: 0, left: 0, behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
}

