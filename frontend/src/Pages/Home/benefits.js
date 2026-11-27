export const membershipBenefits = Object.freeze({ fee: 599, wallet: 599, coupons: 1000, couponStatus: 'Proposed' });
export const calculatorDefaults = Object.freeze({ orders: 20, value: 1000, rate: 5 });
export const currency = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value);
export const estimateCommission = ({ orders, value, rate }) => orders * value * rate / 100;
