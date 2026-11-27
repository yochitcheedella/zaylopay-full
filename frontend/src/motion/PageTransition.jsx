export default function PageTransition({ routeKey, children }) {
  return (
    <div key={routeKey} className="zp-page-stage">
      {children}
    </div>
  );
}
