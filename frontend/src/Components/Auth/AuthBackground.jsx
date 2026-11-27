import React from 'react';
import './AuthBackground.css';

/**
 * Full-page animated pastel lime / sage / mint gradient backdrop with thin,
 * gently drifting looping SVG decorative curves anchored behind authentication cards.
 */
export default function AuthBackground() {
  return (
    <div className="zp-auth-bg" aria-hidden="true">
      <div className="zp-auth-bg-glow zp-auth-bg-glow-lime" />
      <div className="zp-auth-bg-glow zp-auth-bg-glow-sage" />
      <div className="zp-auth-bg-glow zp-auth-bg-glow-mint" />

      <svg
        className="zp-auth-bg-svg"
        viewBox="0 0 1440 900"
        preserveAspectRatio="xMidYMid slice"
        focusable="false"
      >
        {/* Top-left thin looping decorative curves */}
        <g className="zp-curve-group zp-curve-tl">
          <path
            d="M-70 250 C 45 190, 95 70, 215 95 C 325 118, 255 245, 165 215 C 88 190, 145 55, 295 24 C 385 6, 465 -18, 520 -45"
            stroke="var(--zp-bg-curve)"
            strokeWidth="1.75"
            strokeLinecap="round"
            fill="none"
          />
          <path
            d="M-90 120 C 20 82, 110 -15, 225 14"
            stroke="var(--zp-bg-curve)"
            strokeWidth="1.25"
            strokeLinecap="round"
            strokeOpacity="0.6"
            fill="none"
          />
        </g>

        {/* Outer top-right subtle balance curve */}
        <g className="zp-curve-group zp-curve-tr">
          <path
            d="M1120 -35 C 1185 88, 1295 142, 1495 118"
            stroke="var(--zp-bg-curve)"
            strokeWidth="1.35"
            strokeLinecap="round"
            strokeOpacity="0.55"
            fill="none"
          />
        </g>

        {/* Bottom-left subtle outer edge curve */}
        <g className="zp-curve-group zp-curve-bl">
          <path
            d="M-65 735 C 62 708, 145 772, 232 930"
            stroke="var(--zp-bg-curve)"
            strokeWidth="1.35"
            strokeLinecap="round"
            strokeOpacity="0.55"
            fill="none"
          />
        </g>

        {/* Bottom-right large sweeping looping curves */}
        <g className="zp-curve-group zp-curve-br">
          <path
            d="M990 945 C 1075 795, 1225 790, 1255 685 C 1282 595, 1175 568, 1142 640 C 1112 708, 1215 765, 1345 705 C 1418 672, 1465 628, 1515 585"
            stroke="var(--zp-bg-curve)"
            strokeWidth="1.8"
            strokeLinecap="round"
            fill="none"
          />
          <path
            d="M1135 940 C 1245 848, 1375 812, 1510 792"
            stroke="var(--zp-bg-curve)"
            strokeWidth="1.3"
            strokeLinecap="round"
            strokeOpacity="0.62"
            fill="none"
          />
        </g>
      </svg>
    </div>
  );
}
