export default function Icon({ name = 'bag', ...props }) {
  const paths = {
    link: <path d="m10 8 3-3a4 4 0 0 1 6 6l-3 3m-2 2-3 3a4 4 0 0 1-6-6l3-3m1 5 6-6"/>,
    bag: <><path d="M5 7h14l1 14H4L5 7Z"/><path d="M9 8V6a3 3 0 0 1 6 0v2"/></>,
    wallet: <><path d="M20 8H5a2 2 0 0 1 0-4h13v4M4 6v13h16V8"/><path d="M16 12h5v4h-5z"/></>,
    ticket: <><path d="M3 6h18v4a2 2 0 0 0 0 4v4H3v-4a2 2 0 0 0 0-4V6Z"/><path d="M15 6v2m0 3v2m0 3v2M7 10h4m-4 4h3"/></>,
    share: <><circle cx="6" cy="12" r="3"/><circle cx="18" cy="5" r="3"/><circle cx="18" cy="19" r="3"/><path d="m9 10 6-4m-6 8 6 4"/></>,
    user: <><circle cx="12" cy="7" r="4"/><path d="M4 21v-3a8 8 0 0 1 16 0v3M17 11l2 2 3-3"/></>,
    reward: <><circle cx="12" cy="12" r="10"/><path d="M8 7h8M8 10h8m-7-3c6 0 6 6 0 6l6 5"/></>,
    arrow: <path d="M4 12h15m-6-6 6 6-6 6"/>,
    check: <path d="m5 12 4 4L19 6"/>,
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...props}>{paths[name]}</svg>;
}

