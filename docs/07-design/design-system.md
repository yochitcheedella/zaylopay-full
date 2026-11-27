# Design System

Extracted from `DESIGN.md`, `frontend/src/index.css`, and component stylesheets:

- **Brand Palette**: Primary Green `#00B99A`, Dark Navy `#101B2D`, Surface `#FFFFFF` / `#FBFDFB`, Border `#DCE7DF`, Muted Ink `#52675C`.
- **Typography**: `Inter` with platform sans-serif fallbacks.
- **Shared Controls**: `LoadingButton`, `SecondaryButton`, `FormControls.jsx` with visible labels, focus rings, and `aria-busy` states.
- **Motion**: `MotionProvider` (`frontend/src/motion/`) respects `prefers-reduced-motion`.
- **Responsive Breakpoints**: Mobile-first Flex/Grid layouts with `@media (max-width: 560px)` stacking for action groups (e.g., `.zp-ms-receipt-actions`).
