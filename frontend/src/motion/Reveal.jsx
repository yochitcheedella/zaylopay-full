export function StaggerGroup({
  as = 'div',
  className = '',
  children,
  ...props
}) {
  const Component = as;
  return (
    <Component className={`zp-reveal-group ${className}`.trim()} data-reveal {...props}>
      {children}
    </Component>
  );
}

export function RevealItem({
  as = 'div',
  index = 0,
  className = '',
  style,
  children,
  ...props
}) {
  const Component = as;
  return (
    <Component
      className={`zp-stagger-item ${className}`.trim()}
      style={{ '--zp-stagger-index': index, ...style }}
      {...props}
    >
      {children}
    </Component>
  );
}
