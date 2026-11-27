import { useEffect, useRef } from 'react';
import Icon from './Icon';
import { currency, membershipBenefits as benefits } from './benefits';

export default function HeroMembershipVisual() {
  const tilt = useRef(null);
  const frame = useRef(0);

  useEffect(() => () => cancelAnimationFrame(frame.current), []);

  const reset = () => {
    cancelAnimationFrame(frame.current);
    if (tilt.current) tilt.current.style.transform = '';
  };

  const move = event => {
    if (
      event.pointerType !== 'mouse' ||
      !matchMedia('(hover: hover) and (pointer: fine)').matches ||
      matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      return;
    }
    const rect = event.currentTarget.getBoundingClientRect();
    const x = (event.clientX - rect.left) / rect.width - 0.5;
    const y = (event.clientY - rect.top) / rect.height - 0.5;
    cancelAnimationFrame(frame.current);
    frame.current = requestAnimationFrame(() => {
      if (tilt.current) {
        tilt.current.style.transform = `rotateX(${-y * 6}deg) rotateY(${x * 8}deg)`;
      }
    });
  };

  return (
    <div className="zp-visual" aria-label="Membership benefits summary">
      <div className="zp-visual-orbit" aria-hidden="true" />
      <span className="zp-spark zp-ambient-motion" aria-hidden="true">✦</span>
      <div className="zp-shopping-bag zp-ambient-motion" aria-hidden="true">
        <Icon name="bag" />
        <span>Zaylo</span>
      </div>
      <span className="zp-coin zp-ambient-motion" aria-hidden="true">₹</span>

      <div className="zp-card-position" onPointerMove={move} onPointerLeave={reset}>
        <div className="zp-card-tilt" ref={tilt}>
          <div className="zp-member-card">
            <span className="zp-member-face zp-member-front">
              <span className="zp-member-top">
                <b>
                  Zaylo<span>Pe</span>
                </b>
                <span>MEMBERSHIP</span>
              </span>
              <span className="zp-chip" aria-hidden="true" />
              <span className="zp-member-price">
                {currency(benefits.fee)}
                <small>One membership. Shopping &amp; referral benefits included.</small>
              </span>
              <span className="zp-member-bottom">
                <span>SHOP · SHARE · EARN</span>
                <span>Lifetime access plan</span>
              </span>
            </span>
          </div>
        </div>
      </div>

      <div className="zp-visual-benefits">
        <div className="zp-wallet-card is-open">
          <span className="zp-wallet-symbol" aria-hidden="true">
            <Icon name="wallet" />
            <i>₹</i>
          </span>
          <span className="zp-small-label">SHOPPING WALLET</span>
          <strong>{currency(benefits.wallet)}</strong>
          <small>For eligible Zaylo products</small>
        </div>
        <div className="zp-coupon-card is-revealed">
          <Icon name="ticket" />
          <span className="zp-small-label">PROPOSED COUPONS</span>
          <strong>{currency(benefits.coupons)}</strong>
          <small>Share savings, not cash</small>
          <span className="zp-particles" aria-hidden="true">
            ✦ · ✧
          </span>
        </div>
      </div>

      <div className="zp-commission-note">
        <span aria-hidden="true">
          <Icon name="share" />
        </span>
        <div>
          <b>Good finds. Shared.</b>
          <small>Qualifying referrals → commissions</small>
        </div>
        <Icon name="reward" />
      </div>
      <p className="zp-visual-caption">Your benefits, connected to the Zaylo ecosystem.</p>
    </div>
  );
}
