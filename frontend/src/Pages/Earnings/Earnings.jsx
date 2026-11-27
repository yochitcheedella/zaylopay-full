import { useEffect, useState, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  TrendingUp,
  IndianRupee,
  Users,
  Link2,
  Copy,
  Check,
  Clock,
  ArrowDownToLine,
  Home,
  LogOut,
  ChevronRight,
  Sparkles,
  Wallet,
  Info,
  Calendar,
  Filter,
  MousePointerClick,
  ShoppingBag,
  Share2,
  Wand2,
  Gift,
  ArrowUpRight,
  Banknote,
  X,
} from 'lucide-react';
import API from '../../Services/api';
import AuthLayout from '../../Components/Auth/AuthLayout';
import { AuthButton, AuthError, LoadingState } from '../../Components/Auth/AuthComponents';
import './Earnings.css';

// ─── Constants ───────────────────────────────────────────────────────────────

const MIN_WITHDRAWAL_PAISE = 10000; // ₹100.00 minimum payout threshold

const PARTNER_STORES = [
  { id: 'flipkart', name: 'Flipkart',  category: 'Shopping',    rate: 'Up to 7%',   tag: 'Popular', url: 'https://www.flipkart.com' },
  { id: 'amazon',   name: 'Amazon',    category: 'Marketplace', rate: 'Up to 9%',   tag: 'Hot',     url: 'https://www.amazon.in' },
  { id: 'myntra',   name: 'Myntra',    category: 'Fashion',     rate: 'Up to 8%',   tag: 'Trending',url: 'https://www.myntra.com' },
  { id: 'ajio',     name: 'AJIO',      category: 'Fashion',     rate: 'Up to 10%',  tag: 'High %',  url: 'https://www.ajio.com' },
  { id: 'hdfc',     name: 'HDFC Bank', category: 'Finance',     rate: 'Flat ₹500',  tag: 'BFSI',    url: 'https://www.hdfcbank.com' },
  { id: 'croma',    name: 'Croma',     category: 'Electronics', rate: 'Up to 4.5%', tag: 'Tech',    url: 'https://www.croma.com' },
];

const HOW_IT_WORKS_STEPS = [
  {
    step: '01',
    title: 'Convert Any Product Link',
    desc: 'Paste a shopping or finance URL into the Profit Link Converter to generate your tracked ZayloPe link.',
  },
  {
    step: '02',
    title: 'Share With Your Network',
    desc: 'Post your link on WhatsApp, Telegram, or social channels where friends and followers shop.',
  },
  {
    step: '03',
    title: 'Earn & Withdraw to Bank',
    desc: 'Every confirmed order credits commission to your ZayloPe wallet — withdraw anytime above ₹100.',
  },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatINR(paise) {
  if (paise == null) return '₹0.00';
  const rupees = Number(paise) / 100;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: 2,
  }).format(rupees);
}

function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    return new Intl.DateTimeFormat('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      timeZone: 'Asia/Kolkata',
    }).format(new Date(isoString));
  } catch {
    return '—';
  }
}

function statusLabel(status) {
  switch (status?.toUpperCase()) {
    case 'PENDING':   return { text: 'Pending',   cls: 'zp-earn-badge--pending' };
    case 'APPROVED':  return { text: 'Approved',  cls: 'zp-earn-badge--approved' };
    case 'PAID':      return { text: 'Paid',      cls: 'zp-earn-badge--paid' };
    case 'REJECTED':  return { text: 'Rejected',  cls: 'zp-earn-badge--rejected' };
    default:          return { text: status ?? '—', cls: '' };
  }
}

function buildProfitLink(rawUrl, affiliateId) {
  const clean = rawUrl.trim();
  if (!clean) return '';
  const code = affiliateId || 'MEMBER';
  const encoded = encodeURIComponent(clean);
  return `${window.location.origin}/register?ref=${encodeURIComponent(code)}&target=${encoded}`;
}

// ─── Sub-components ──────────────────────────────────────────────────────────

function StatCard({ icon: Icon, label, value, sub, accent }) {
  return (
    <div className={`zp-earn-stat-card${accent ? ' zp-earn-stat-card--accent' : ''}`}>
      <div className="zp-earn-stat-top">
        <span className="zp-earn-stat-icon" aria-hidden="true">
          <Icon size={17} strokeWidth={2.1} />
        </span>
        <span className="zp-earn-stat-label">{label}</span>
      </div>
      <span className="zp-earn-stat-value">{value}</span>
      {sub && <span className="zp-earn-stat-sub">{sub}</span>}
    </div>
  );
}

function EarningRow({ item }) {
  const { text, cls } = statusLabel(item.status);
  return (
    <div className="zp-earn-row" role="row">
      <span className="zp-earn-row-date">{formatDate(item.createdAt)}</span>
      <span className="zp-earn-row-desc">{item.description ?? 'Referral commission'}</span>
      <span className="zp-earn-row-ref">{item.referenceId ?? '—'}</span>
      <span className="zp-earn-row-amount">{formatINR(item.amountPaise)}</span>
      <span className={`zp-earn-badge ${cls}`}>{text}</span>
    </div>
  );
}

function EmptyEarnings() {
  return (
    <div className="zp-earn-empty" role="status">
      <span className="zp-earn-empty-icon" aria-hidden="true">
        <Wallet size={36} strokeWidth={1.5} />
      </span>
      <p className="zp-earn-empty-title">No transactions yet</p>
      <p className="zp-earn-empty-sub">
        Convert a product link or share your referral link to start tracking commissions here.
      </p>
    </div>
  );
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function EarningsPage() {
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [membership, setMembership] = useState(null);
  const [earnings, setEarnings] = useState(null);
  const [loggingOut, setLoggingOut] = useState(false);

  // Navigation tabs (ExtraPe template structure)
  const [activeTab, setActiveTab] = useState('overview');

  // Copy states
  const [copiedId, setCopiedId] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);
  const [copiedStoreId, setCopiedStoreId] = useState(null);

  // Link converter state
  const [inputUrl, setInputUrl] = useState('');
  const [converterError, setConverterError] = useState('');
  const [generatedLink, setGeneratedLink] = useState('');
  const [copiedGenerated, setCopiedGenerated] = useState(false);
  const [recentLinks, setRecentLinks] = useState([]);

  // Transaction filter
  const [filter, setFilter] = useState('ALL');

  // Withdraw drawer toggle
  const [showWithdrawBox, setShowWithdrawBox] = useState(false);

  // ── Auth + data load ──────────────────────────────────────────────────────
  const fetchEarningsPayload = useCallback(async () => {
    const sessionRes = await API.get('/auth/session');
    const session = sessionRes?.data;

    if (!session?.authenticated || session?.sessionState !== 'ACTIVE') {
      return { redirect: '/login' };
    }
    if (!session.canAccessDashboard) {
      return { redirect: '/register' };
    }

    const [memberRes, earningsRes] = await Promise.allSettled([
      API.get('/applicants/me/membership'),
      API.get('/applicants/me/earnings'),
    ]);

    return {
      membershipData: memberRes.status === 'fulfilled' ? (memberRes.value?.data ?? null) : undefined,
      earningsData: earningsRes.status === 'fulfilled' ? (earningsRes.value?.data ?? null) : undefined,
    };
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      const result = await fetchEarningsPayload();
      if (result.redirect) { navigate(result.redirect, { replace: true }); return; }
      if (result.membershipData !== undefined) setMembership(result.membershipData);
      if (result.earningsData !== undefined) setEarnings(result.earningsData);
    } catch (err) {
      const status = err.response?.status;
      if (status === 401) { navigate('/login', { replace: true }); return; }
      if (status === 403) { navigate('/register', { replace: true }); return; }
      setLoadError(
        err.response?.data?.detail ??
          'Unable to load your earnings. Please check your connection and try again.',
      );
    } finally {
      setLoading(false);
    }
  }, [fetchEarningsPayload, navigate]);

  useEffect(() => {
    let active = true;
    async function loadOnMount() {
      try {
        const result = await fetchEarningsPayload();
        if (!active) return;
        if (result.redirect) { navigate(result.redirect, { replace: true }); return; }
        if (result.membershipData !== undefined) setMembership(result.membershipData);
        if (result.earningsData !== undefined) setEarnings(result.earningsData);
      } catch (err) {
        if (!active) return;
        const status = err.response?.status;
        if (status === 401) { navigate('/login', { replace: true }); return; }
        if (status === 403) { navigate('/register', { replace: true }); return; }
        setLoadError(
          err.response?.data?.detail ??
            'Unable to load your earnings. Please check your connection and try again.',
        );
      } finally {
        if (active) setLoading(false);
      }
    }
    loadOnMount();
    return () => { active = false; };
  }, [fetchEarningsPayload, navigate]);

  // ── Actions ───────────────────────────────────────────────────────────────
  const referralUrl = membership?.affiliateId
    ? `${window.location.origin}/register?ref=${membership.affiliateId}`
    : null;

  const copyText = async (text, onDone) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      onDone();
    } catch {
      /* clipboard blocked */
    }
  };

  const handleCopyId = () => {
    copyText(membership?.affiliateId, () => {
      setCopiedId(true);
      setTimeout(() => setCopiedId(false), 2000);
    });
  };

  const handleCopyLink = () => {
    copyText(referralUrl, () => {
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    });
  };

  const handleConvertLink = (e) => {
    e.preventDefault();
    setConverterError('');
    const trimmed = inputUrl.trim();
    if (!trimmed) {
      setConverterError('Paste a product or store URL to convert.');
      return;
    }
    let normalized = trimmed;
    if (!/^https?:\/\//i.test(normalized)) {
      normalized = `https://${normalized}`;
    }
    try {
      new URL(normalized);
    } catch {
      setConverterError('Please enter a valid URL (e.g. https://www.flipkart.com/...).');
      return;
    }

    const profitUrl = buildProfitLink(normalized, membership?.affiliateId);
    setGeneratedLink(profitUrl);
    setRecentLinks((prev) => [
      { id: Date.now(), source: normalized, profitUrl, createdAt: new Date().toISOString() },
      ...prev.slice(0, 4),
    ]);
  };

  const handleCopyGenerated = () => {
    copyText(generatedLink, () => {
      setCopiedGenerated(true);
      setTimeout(() => setCopiedGenerated(false), 2000);
    });
  };

  const handleCopyStoreLink = (store) => {
    const link = buildProfitLink(store.url, membership?.affiliateId);
    copyText(link, () => {
      setCopiedStoreId(store.id);
      setTimeout(() => setCopiedStoreId(null), 2000);
    });
  };

  const handleLogout = async () => {
    setLoggingOut(true);
    try { await API.post('/auth/logout'); } catch { /* ignore */ }
    sessionStorage.removeItem('zaylopeToken');
    sessionStorage.removeItem('zaylopeUserName');
    sessionStorage.removeItem('zaylopeEmail');
    navigate('/login', { replace: true });
  };

  const handleDownloadCsv = (rowsData) => {
    const rows = [
      ['Date', 'Description', 'Reference', 'Amount (INR)', 'Status'],
      ...rowsData.map((t) => [
        formatDate(t.createdAt),
        `"${(t.description ?? 'Referral commission').replace(/"/g, '""')}"`,
        t.referenceId ?? '',
        ((t.amountPaise ?? 0) / 100).toFixed(2),
        t.status ?? '',
      ]),
    ];
    const csv = rows.map((r) => r.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `zaylope-earnings-${Date.now()}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  // ── Derived values ────────────────────────────────────────────────────────
  const summary = earnings?.summary ?? {};
  const transactions = Array.isArray(earnings?.transactions) ? earnings.transactions : [];

  const filteredTxns = filter === 'ALL'
    ? transactions
    : transactions.filter((t) => t.status?.toUpperCase() === filter);

  const totalEarnedPaise  = summary.totalEarnedPaise ?? 0;
  const totalPendingPaise = summary.totalPendingPaise ?? 0;
  const totalPaidPaise    = summary.totalPaidPaise ?? 0;
  const availablePaise    = summary.availablePaise ?? Math.max(0, totalEarnedPaise - totalPendingPaise - totalPaidPaise);

  const totalEarned   = formatINR(totalEarnedPaise);
  const totalPending  = formatINR(totalPendingPaise);
  const totalPaid     = formatINR(totalPaidPaise);
  const availableAmt  = formatINR(availablePaise);
  const referralCount = summary.referralCount ?? 0;
  const totalClicks   = summary.totalClicks ?? 0;
  const totalOrders   = summary.totalOrders ?? transactions.length;
  const nextPayout    = summary.nextPayoutDate ? formatDate(summary.nextPayoutDate) : 'TBD';

  const withdrawProgress = Math.min(100, Math.round((availablePaise / MIN_WITHDRAWAL_PAISE) * 100));
  const canWithdraw = availablePaise >= MIN_WITHDRAWAL_PAISE;

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <AuthLayout cardSize="register" data-testid="earnings-page">
      {loading ? (
        <LoadingState label="Loading your earnings…" />
      ) : loadError ? (
        <div className="zp-earn-error-box">
          <AuthError message={loadError} />
          <div className="zp-earn-error-actions">
            <AuthButton type="button" onClick={load}>Retry</AuthButton>
            <AuthButton type="button" variant="secondary" loading={loggingOut} onClick={handleLogout}>
              Log out
            </AuthButton>
          </div>
        </div>
      ) : (
        <div className="zp-earn-view" data-testid="earnings-view">

          {/* ── Page Header ── */}
          <div className="zp-earn-header">
            <span className="zp-earn-pill">
              <Sparkles size={13} strokeWidth={2.2} aria-hidden="true" />
              ZayloPe Profit Hub
            </span>
            <h1 className="zp-earn-title">My Earnings</h1>
            <p className="zp-earn-subtitle">
              Convert product links, share top partner deals, and withdraw your commissions.
            </p>
          </div>

          {/* ── ExtraPe-style Dashboard Tab Bar ── */}
          <nav className="zp-earn-tabs" aria-label="Earnings sections">
            {[
              { id: 'overview',  label: 'Overview',     icon: Wallet },
              { id: 'converter', label: 'Make Link',    icon: Wand2 },
              { id: 'reports',   label: 'Reports',      icon: TrendingUp },
              { id: 'refer',     label: 'Refer & Earn', icon: Gift },
            ].map(({ id, label, icon: TabIcon }) => (
              <button
                key={id}
                type="button"
                className={`zp-earn-tab${activeTab === id ? ' is-active' : ''}`}
                onClick={() => setActiveTab(id)}
                aria-pressed={activeTab === id}
              >
                <TabIcon size={14} strokeWidth={2.2} aria-hidden="true" />
                <span>{label}</span>
              </button>
            ))}
          </nav>

          {/* ═══════════════════════════════════════════════════════════════ */}
          {/* TAB: OVERVIEW                                                   */}
          {/* ═══════════════════════════════════════════════════════════════ */}
          {activeTab === 'overview' && (
            <>
              {/* ── Wallet Hero Card (ExtraPe Balance + Withdraw CTA) ── */}
              <section className="zp-earn-wallet-hero" aria-label="Wallet balance">
                <div className="zp-earn-wallet-top">
                  <div>
                    <span className="zp-earn-wallet-kicker">Available to Withdraw</span>
                    <div className="zp-earn-wallet-balance">{availableAmt}</div>
                  </div>
                  <button
                    type="button"
                    className="zp-earn-withdraw-btn"
                    onClick={() => setShowWithdrawBox((v) => !v)}
                  >
                    <Banknote size={16} strokeWidth={2.1} aria-hidden="true" />
                    <span>Withdraw</span>
                  </button>
                </div>

                {/* Threshold progress bar */}
                <div className="zp-earn-threshold">
                  <div className="zp-earn-threshold-row">
                    <span>Minimum payout: ₹100.00</span>
                    <span>{withdrawProgress}%</span>
                  </div>
                  <div className="zp-earn-progress-track" role="progressbar" aria-valuenow={withdrawProgress} aria-valuemin={0} aria-valuemax={100}>
                    <div className="zp-earn-progress-fill" style={{ width: `${withdrawProgress}%` }} />
                  </div>
                </div>

                {/* Mini summary strip inside hero */}
                <div className="zp-earn-wallet-strip">
                  <div className="zp-earn-wallet-mini">
                    <span>Total Earned</span>
                    <strong>{totalEarned}</strong>
                  </div>
                  <div className="zp-earn-wallet-mini">
                    <span>Pending</span>
                    <strong>{totalPending}</strong>
                  </div>
                  <div className="zp-earn-wallet-mini">
                    <span>Total Paid</span>
                    <strong>{totalPaid}</strong>
                  </div>
                </div>

                {/* Inline Withdraw Panel */}
                {showWithdrawBox && (
                  <div className="zp-earn-withdraw-panel" role="region" aria-label="Withdrawal details">
                    <div className="zp-earn-withdraw-panel-head">
                      <strong>Bank / UPI Payout</strong>
                      <button
                        type="button"
                        className="zp-earn-icon-close"
                        onClick={() => setShowWithdrawBox(false)}
                        aria-label="Close withdrawal panel"
                      >
                        <X size={15} />
                      </button>
                    </div>
                    {canWithdraw ? (
                      <p className="zp-earn-withdraw-msg">
                        Your confirmed balance is ready. Payouts are processed to your registered bank/UPI on the next cycle ({nextPayout}).
                      </p>
                    ) : (
                      <p className="zp-earn-withdraw-msg">
                        Reach {formatINR(MIN_WITHDRAWAL_PAISE)} in confirmed earnings to unlock instant bank or UPI transfer.
                      </p>
                    )}
                  </div>
                )}
              </section>

              {/* ── 4-Tile Performance KPI Grid ── */}
              <div className="zp-earn-stats" role="list" aria-label="Performance summary">
                <StatCard
                  icon={MousePointerClick}
                  label="Total Clicks"
                  value={totalClicks}
                  sub="Link visits"
                />
                <StatCard
                  icon={ShoppingBag}
                  label="Orders"
                  value={totalOrders}
                  sub="Tracked sales"
                />
                <StatCard
                  icon={Clock}
                  label="Pending"
                  value={totalPending}
                  sub="Awaiting approval"
                />
                <StatCard
                  icon={Users}
                  label="Referrals"
                  value={referralCount}
                  sub="Active invites"
                />
              </div>

              {/* ── Quick Link Converter Widget ── */}
              <section className="zp-earn-converter-card" aria-label="Quick profit link converter">
                <div className="zp-earn-section-head">
                  <div>
                    <h2 className="zp-earn-section-title">
                      <Wand2 size={16} strokeWidth={2.2} aria-hidden="true" />
                      Make Profit Link
                    </h2>
                    <p className="zp-earn-section-sub">
                      Paste any product URL from our partner stores to create your earning link.
                    </p>
                  </div>
                </div>

                <form className="zp-earn-converter-form" onSubmit={handleConvertLink}>
                  <input
                    type="url"
                    className="zp-earn-converter-input"
                    placeholder="https://www.flipkart.com/product-url..."
                    value={inputUrl}
                    onChange={(e) => setInputUrl(e.target.value)}
                    aria-label="Product URL to convert"
                  />
                  <button type="submit" className="zp-earn-converter-btn">
                    Convert Link
                  </button>
                </form>

                {converterError && <p className="zp-earn-converter-err">{converterError}</p>}

                {generatedLink && (
                  <div className="zp-earn-generated-box">
                    <span className="zp-earn-generated-label">Your Profit Link</span>
                    <div className="zp-earn-generated-row">
                      <span className="zp-earn-generated-url" title={generatedLink}>
                        {generatedLink}
                      </span>
                      <button
                        type="button"
                        className={`zp-earn-copy-pill${copiedGenerated ? ' is-copied' : ''}`}
                        onClick={handleCopyGenerated}
                      >
                        {copiedGenerated ? <Check size={14} /> : <Copy size={14} />}
                        <span>{copiedGenerated ? 'Copied!' : 'Copy'}</span>
                      </button>
                    </div>
                    <div className="zp-earn-share-row">
                      <a
                        href={`https://wa.me/?text=${encodeURIComponent(`Check out this deal: ${generatedLink}`)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="zp-earn-share-btn zp-earn-share-btn--wa"
                      >
                        <Share2 size={13} aria-hidden="true" /> WhatsApp
                      </a>
                      <a
                        href={`https://t.me/share/url?url=${encodeURIComponent(generatedLink)}&text=${encodeURIComponent('Check out this deal!')}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="zp-earn-share-btn zp-earn-share-btn--tg"
                      >
                        <Share2 size={13} aria-hidden="true" /> Telegram
                      </a>
                    </div>
                  </div>
                )}
              </section>

              {/* ── Top Partner Stores Grid (ExtraPe Store Cards) ── */}
              <section className="zp-earn-stores-section" aria-label="Top partner stores">
                <div className="zp-earn-section-head">
                  <div>
                    <h2 className="zp-earn-section-title">
                      <ShoppingBag size={16} strokeWidth={2.2} aria-hidden="true" />
                      Top Earning Stores
                    </h2>
                    <p className="zp-earn-section-sub">
                      Copy your instant store link and earn commission on every order.
                    </p>
                  </div>
                </div>

                <div className="zp-earn-stores-grid">
                  {PARTNER_STORES.map((store) => {
                    const isCopied = copiedStoreId === store.id;
                    return (
                      <div key={store.id} className="zp-earn-store-card">
                        <div className="zp-earn-store-top">
                          <span className="zp-earn-store-avatar" aria-hidden="true">
                            {store.name.slice(0, 2).toUpperCase()}
                          </span>
                          <span className="zp-earn-store-tag">{store.tag}</span>
                        </div>
                        <div className="zp-earn-store-name">{store.name}</div>
                        <div className="zp-earn-store-cat">{store.category}</div>
                        <div className="zp-earn-store-rate">{store.rate}</div>
                        <button
                          type="button"
                          className={`zp-earn-store-btn${isCopied ? ' is-copied' : ''}`}
                          onClick={() => handleCopyStoreLink(store)}
                        >
                          {isCopied ? <Check size={13} /> : <Link2 size={13} />}
                          <span>{isCopied ? 'Link Copied' : 'Copy Link'}</span>
                        </button>
                      </div>
                    );
                  })}
                </div>
              </section>

              {/* ── How It Works (3-Step Strip) ── */}
              <section className="zp-earn-steps-section" aria-label="How earning works">
                <h2 className="zp-earn-section-title">How It Works</h2>
                <div className="zp-earn-steps-grid">
                  {HOW_IT_WORKS_STEPS.map((s) => (
                    <div key={s.step} className="zp-earn-step-card">
                      <span className="zp-earn-step-num">{s.step}</span>
                      <strong className="zp-earn-step-title">{s.title}</strong>
                      <p className="zp-earn-step-desc">{s.desc}</p>
                    </div>
                  ))}
                </div>
              </section>
            </>
          )}

          {/* ═══════════════════════════════════════════════════════════════ */}
          {/* TAB: MAKE LINK (DEDICATED CONVERTER)                            */}
          {/* ═══════════════════════════════════════════════════════════════ */}
          {activeTab === 'converter' && (
            <>
              <section className="zp-earn-converter-card" aria-label="Profit link converter">
                <div className="zp-earn-section-head">
                  <div>
                    <h2 className="zp-earn-section-title">
                      <Wand2 size={16} strokeWidth={2.2} aria-hidden="true" />
                      Profit Link Converter
                    </h2>
                    <p className="zp-earn-section-sub">
                      Turn any supported product URL into a trackable ZayloPe earning link.
                    </p>
                  </div>
                </div>

                <form className="zp-earn-converter-form" onSubmit={handleConvertLink}>
                  <input
                    type="url"
                    className="zp-earn-converter-input"
                    placeholder="Paste product URL (Flipkart, Amazon, Myntra, AJIO...)"
                    value={inputUrl}
                    onChange={(e) => setInputUrl(e.target.value)}
                    aria-label="Product URL to convert"
                  />
                  <button type="submit" className="zp-earn-converter-btn">
                    Make Profit Link
                  </button>
                </form>

                {converterError && <p className="zp-earn-converter-err">{converterError}</p>}

                {generatedLink && (
                  <div className="zp-earn-generated-box">
                    <span className="zp-earn-generated-label">Ready to Share</span>
                    <div className="zp-earn-generated-row">
                      <span className="zp-earn-generated-url" title={generatedLink}>
                        {generatedLink}
                      </span>
                      <button
                        type="button"
                        className={`zp-earn-copy-pill${copiedGenerated ? ' is-copied' : ''}`}
                        onClick={handleCopyGenerated}
                      >
                        {copiedGenerated ? <Check size={14} /> : <Copy size={14} />}
                        <span>{copiedGenerated ? 'Copied!' : 'Copy'}</span>
                      </button>
                    </div>
                  </div>
                )}
              </section>

              {/* Recent converted links */}
              <section className="zp-earn-recent-card" aria-label="Recently converted links">
                <h2 className="zp-earn-section-title">Recent Profit Links</h2>
                {recentLinks.length === 0 ? (
                  <p className="zp-earn-section-sub">
                    Links you convert in this session will appear here for quick re-sharing.
                  </p>
                ) : (
                  <div className="zp-earn-recent-list">
                    {recentLinks.map((item) => (
                      <div key={item.id} className="zp-earn-recent-item">
                        <div className="zp-earn-recent-info">
                          <span className="zp-earn-recent-src" title={item.source}>{item.source}</span>
                          <span className="zp-earn-recent-out" title={item.profitUrl}>{item.profitUrl}</span>
                        </div>
                        <button
                          type="button"
                          className="zp-earn-copy-pill"
                          onClick={() => copyText(item.profitUrl, () => {})}
                        >
                          <ArrowUpRight size={13} /> Copy
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </>
          )}

          {/* ═══════════════════════════════════════════════════════════════ */}
          {/* TAB: REPORTS (TRANSACTIONS + PAYOUT NOTICE)                     */}
          {/* ═══════════════════════════════════════════════════════════════ */}
          {activeTab === 'reports' && (
            <>
              <div className="zp-earn-payout-notice" role="note">
                <Calendar size={16} strokeWidth={2} className="zp-earn-payout-icon" aria-hidden="true" />
                <span>
                  <strong>Next payout cycle:</strong> {nextPayout}
                </span>
              </div>

              {earnings === null ? (
                <div className="zp-earn-info-banner" role="note">
                  <Info size={17} strokeWidth={2} className="zp-earn-info-icon" aria-hidden="true" />
                  <div>
                    <strong>Earnings ledger syncing</strong>
                    <p>
                      Detailed commission history, payout schedules, and click analytics will
                      appear here as soon as your first tracked order is recorded.
                    </p>
                  </div>
                </div>
              ) : (
                <section className="zp-earn-txn-section" aria-label="Transaction history">
                  <div className="zp-earn-txn-header">
                    <h2 className="zp-earn-txn-title">
                      <TrendingUp size={17} strokeWidth={2.1} aria-hidden="true" />
                      Order &amp; Commission History
                    </h2>

                    <div className="zp-earn-filter-row" role="group" aria-label="Filter transactions">
                      <Filter size={14} strokeWidth={2} className="zp-earn-filter-icon" aria-hidden="true" />
                      {['ALL', 'PENDING', 'APPROVED', 'PAID', 'REJECTED'].map((f) => (
                        <button
                          key={f}
                          type="button"
                          className={`zp-earn-filter-btn${filter === f ? ' is-active' : ''}`}
                          onClick={() => setFilter(f)}
                          aria-pressed={filter === f}
                        >
                          {f === 'ALL' ? 'All' : f.charAt(0) + f.slice(1).toLowerCase()}
                        </button>
                      ))}
                    </div>
                  </div>

                  {filteredTxns.length === 0 ? (
                    <EmptyEarnings />
                  ) : (
                    <div className="zp-earn-table" role="table" aria-label="Earnings transactions">
                      <div className="zp-earn-thead" role="rowgroup">
                        <div className="zp-earn-row zp-earn-row--head" role="row">
                          <span>Date</span>
                          <span>Description</span>
                          <span>Reference</span>
                          <span>Amount</span>
                          <span>Status</span>
                        </div>
                      </div>
                      <div role="rowgroup">
                        {filteredTxns.map((item, i) => (
                          <EarningRow key={item.id ?? i} item={item} />
                        ))}
                      </div>
                    </div>
                  )}

                  {filteredTxns.length > 0 && (
                    <button
                      type="button"
                      className="zp-earn-download-btn"
                      onClick={() => handleDownloadCsv(filteredTxns)}
                    >
                      <ArrowDownToLine size={15} strokeWidth={2.1} aria-hidden="true" />
                      Download CSV
                    </button>
                  )}
                </section>
              )}
            </>
          )}

          {/* ═══════════════════════════════════════════════════════════════ */}
          {/* TAB: REFER & EARN (AFFILIATE ID CARD)                           */}
          {/* ═══════════════════════════════════════════════════════════════ */}
          {activeTab === 'refer' && (
            <>
              {membership?.affiliateId ? (
                <div className="zp-earn-id-card" aria-label="Your affiliate details">
                  <div className="zp-earn-id-card-top">
                    <div className="zp-earn-id-brand">
                      <span className="zp-earn-id-z" aria-hidden="true">Z</span>
                      <span className="zp-earn-id-brand-text">
                        Zaylo<span>Pe</span>
                      </span>
                    </div>
                    <span className="zp-earn-id-tier">10% Referral Bonus</span>
                  </div>

                  <div className="zp-earn-id-block">
                    <span className="zp-earn-id-label">Your Affiliate ID</span>
                    <div className="zp-earn-id-row">
                      <code className="zp-earn-id-code">{membership.affiliateId}</code>
                      <button
                        type="button"
                        className={`zp-earn-copy-btn${copiedId ? ' is-copied' : ''}`}
                        onClick={handleCopyId}
                        aria-label={copiedId ? 'Copied!' : 'Copy affiliate ID'}
                      >
                        {copiedId ? <Check size={14} strokeWidth={2.5} /> : <Copy size={14} strokeWidth={2} />}
                        <span>{copiedId ? 'Copied!' : 'Copy'}</span>
                      </button>
                    </div>
                  </div>

                  {referralUrl && (
                    <div className="zp-earn-ref-block">
                      <span className="zp-earn-id-label">Your Referral Link</span>
                      <div className="zp-earn-ref-row">
                        <span className="zp-earn-ref-url" title={referralUrl}>{referralUrl}</span>
                        <button
                          type="button"
                          className={`zp-earn-copy-btn${copiedLink ? ' is-copied' : ''}`}
                          onClick={handleCopyLink}
                          aria-label={copiedLink ? 'Copied!' : 'Copy referral link'}
                        >
                          {copiedLink ? <Check size={14} strokeWidth={2.5} /> : <Link2 size={14} strokeWidth={2} />}
                          <span>{copiedLink ? 'Copied!' : 'Copy Link'}</span>
                        </button>
                      </div>

                      <div className="zp-earn-share-row">
                        <a
                          href={`https://wa.me/?text=${encodeURIComponent(`Join ZayloPe and start earning! Use my referral link: ${referralUrl}`)}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="zp-earn-share-btn zp-earn-share-btn--wa"
                        >
                          WhatsApp
                        </a>
                        <a
                          href={`https://t.me/share/url?url=${encodeURIComponent(referralUrl)}&text=${encodeURIComponent('Join ZayloPe and earn with me!')}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="zp-earn-share-btn zp-earn-share-btn--tg"
                        >
                          Telegram
                        </a>
                      </div>
                    </div>
                  )}
                </div>
              ) : (
                <div className="zp-earn-info-banner" role="note">
                  <Info size={17} strokeWidth={2} className="zp-earn-info-icon" aria-hidden="true" />
                  <div>
                    <strong>Affiliate ID activating</strong>
                    <p>Your unique referral link will appear here once your membership profile is synced.</p>
                  </div>
                </div>
              )}

              <div className="zp-earn-stats" role="list" aria-label="Referral summary">
                <StatCard
                  icon={Users}
                  label="Referred Friends"
                  value={referralCount}
                  sub="Active sign-ups"
                  accent
                />
                <StatCard
                  icon={IndianRupee}
                  label="Referral Bonus"
                  value={totalEarned}
                  sub="Lifetime earned"
                />
              </div>
            </>
          )}

          {/* ── Footer actions ── */}
          <div className="zp-earn-actions">
            <Link to="/dashboard" className="zp-earn-nav-btn zp-earn-nav-btn--primary">
              <ChevronRight size={15} strokeWidth={2.3} aria-hidden="true" />
              Dashboard
            </Link>
            <Link to="/" className="zp-earn-nav-btn zp-earn-nav-btn--secondary">
              <Home size={15} strokeWidth={2.1} aria-hidden="true" />
              Home
            </Link>
            <AuthButton
              type="button"
              variant="secondary"
              loading={loggingOut}
              loadingLabel="Logging out…"
              onClick={handleLogout}
              className="zp-earn-logout-btn"
            >
              <LogOut size={15} strokeWidth={2.1} aria-hidden="true" />
              Log out
            </AuthButton>
          </div>
        </div>
      )}
    </AuthLayout>
  );
}
