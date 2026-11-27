---
name: ZayloPe Registration & Authentication
description: Unified ZayloPe brand design system combining animated pastel green backdrop, expanded single-column white cards, WCAG 2.2 AA contrast, and three visible onboarding stages with an internal success/status view.
colors:
  forest: "#083d31"
  emerald: "#008f63"
  lime: "#d4f578"
  mint: "#dceedd"
  warm: "#f5f8f2"
  bg-lime: "#e9fbcb"
  bg-sage: "#d1e2c9"
  bg-mint: "#bfd5c4"
  bg-curve: "#a5ce76"
  surface: "#ffffff"
  surface-subtle: "#f9fbf9"
  ink: "#142820"
  text-secondary: "#273e33"
  muted: "#52665c"
  accent-text: "#006644"
  border: "#dce7de"
  control-border: "#7c9789"
  valid-bg: "#e6fbe0"
  valid-border: "#79b96c"
  valid-badge: "#08794f"
  error: "#b42318"
  error-bg: "#fff0f1"
  error-border: "#dc3545"
  warning: "#7c4400"
  warning-bg: "#fffbe6"
typography:
  heading:
    fontFamily: "Manrope, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif"
    fontSize: "clamp(26px, 3vw, 32px)"
    fontWeight: 700
    lineHeight: 1.18
    letterSpacing: "-0.025em"
  body:
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif"
    fontSize: "15.5px"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif"
    fontSize: "14px"
    fontWeight: 600
  input:
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif"
    fontSize: "16px"
  action:
    fontFamily: "Inter, -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif"
    fontSize: "15.5px"
    fontWeight: 600
rounded:
  control: "12px"
  card: "24px"
  pill: "9999px"
dimensions:
  input-height: "52px"
  button-height: "52px"
  touch-target-min: "44px"
  card-max-register: "640px"
  card-max-compact: "560px"
  card-max-payment: "680px"
  card-max-review: "760px"
  form-inner-max: "520px"
motion:
  card-enter: "350ms"
  input-focus: "180ms"
  valid-sweep: "350ms"
  checkmark-reveal: "200ms"
  error-transition: "200ms"
  button-hover: "150ms"
  button-press: "100ms"
  step-transition: "250ms"
  curve-float: "8–12s"
  background-glow: "18–24s"
---

# Design System: ZayloPe

## Overview

**Creative North Star: "Calm, tactile momentum"**

ZayloPe's authentication and onboarding routes (`/login` and `/register`) share a single unified design system in `frontend/src/Pages/Register/registrationTokens.css` and `frontend/src/Components/Auth/`. Expanded centered white cards (`24px` radius, `24px–48px` responsive padding) rest stationary over an animated pastel lime/sage/mint gradient canvas (`AuthBackground.jsx`) with thin looping `#A5CE76` SVG curves.

## Color & Contrast Tokens (WCAG 2.2 AA)

- **Animated Background:** Light lime glow (`#E9FBCB` top-center), pastel sage (`#D1E2C9` right), soft mint (`#BFD5C4` bottom-left), decorative looping SVG curves (`#A5CE76`), `pointer-events: none`, `aria-hidden="true"`.
- **Foreground & Text:** Ink `#142820` (`>9.5:1` on pastel grounds and `>14:1` on white), Secondary `#273E33` (used outside cards for links/badges), Muted `#52665C` (`6.06:1` inside white cards), Forest `#083D31` headings and primary buttons, Emerald `#008F63` focus rings and active accents.
- **Validation & Feedback:** Valid format fill `#E6FBE0`, border `#79B96C`, checkmark dot `#08794F` (`5.44:1` white check contrast); error fill `#FFF0F1`, border `#DC3545`, text `#B42318` (`6.07:1` contrast).

## Expanded Card & Layout Architecture

- **Removed Chrome:** No sticky headers, left promotional sidebars, duplicate logos, or heavy hero illustrations inside `/login` or `/register` (public homepage navigation in `PublicLayout` remains intact).
- **Top-Left Navigation:** `BackToHome.jsx` sits outside the top-left of the card using React Router (`/`), maintaining `>= 44px` height and zero overlap with the card.
- **Content-Driven Expanded Card Widths:**
  - `640px`: Manual Registration, Google Profile Completion, Application Status (`cardSize="register" | "status"`)
  - `560px`: Login, Verification Method Selector, Mobile OTP, Email OTP (`cardSize="compact"`)
  - `680px`: Membership Payment Checkout (`cardSize="payment"`)
  - `760px`: Application Review (`cardSize="review"`)
- **Form Controls (`52px` height, `12px` radius):** Leading icons (`User`, `Smartphone`, `Mail`), fixed `40px` right status slot (`0px` horizontal layout shift), lightweight CSS animations (`180ms` focus ring, typing pencil cue, `350ms` left-to-right green format sweep + `200ms` `#08794F` checkmark pop, `#FFF0F1`/`#DC3545` error state), validation delayed until blur or submit, and full `prefers-reduced-motion` static fallback.

## Current Onboarding Flow

1. Account details: full name, Indian mobile and email, with Google profile completion where applicable.
2. Contact verification: mobile before email; verification status remains server-authoritative.
3. Membership payment: consent/submission prerequisites and Razorpay checkout for the configured membership fee.
4. Internal success/status view: verified captured payment and eligible membership produce the Affiliate ID and receipt controls. Pending/restricted states remain distinct.

The visible stepper has three stages; the fourth item is an internal status view. Historical review-card tokens above remain for retained components, rather than describing an additional required stage. See [membership rules](docs/04-modules/membership-and-payment.md) and [known development issues](docs/12-project-status/known-issues.md). This document is a project design reference, not agent instructions or proof of accessibility conformance.
