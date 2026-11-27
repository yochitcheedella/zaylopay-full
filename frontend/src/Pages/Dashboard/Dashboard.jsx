import { useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import API from "../../Services/api";
import "./Dashboard.css";

const menuItems = [
    { icon: "⌂", name: "Home" },
    { icon: "↗", name: "Converter" },
    { icon: "▣", name: "Stores" },
    { icon: "◉", name: "My Account" },
    { icon: "₹", name: "My Earnings" },
    { icon: "↝", name: "My Activity" },
    { icon: "✥", name: "Contests" },
    { icon: "◉", name: "Flash Deals" },
    { icon: "↗", name: "Most Shared Deals" },
    { icon: "★", name: "Best Deals" },
    { icon: "⚙", name: "Settings" }
];

const tutorials = [
    { title: "What is ZAYLOPE?", subtitle: "Learn how to earn money", style: "tutorial-one" },
    { title: "Earning Links", subtitle: "Learn to create earning links", style: "tutorial-two" },
    { title: "How to Find Stores", subtitle: "Explore shopping stores", style: "tutorial-three" },
    { title: "How to Convert Links", subtitle: "Convert links and share", style: "tutorial-four" }
];

const stores = [
    { name: "Zaylo", logo: "Zaylo", style: "zaylo" },
    { name: "Amazon", logo: "amazon.in", style: "amazon" },
    { name: "Flipkart", logo: "Flipkart", style: "flipkart" },
    { name: "AJIO", logo: "AJIO", style: "ajio" },
    { name: "Myntra", logo: "M", style: "myntra" },
    { name: "Shopsy", logo: "SHOPSY", style: "shopsy" },
    { name: "Dot & Key", logo: "DOT & KEY", style: "dotkey" }
];

const banners = [
    {
        title: "Discover Amazing Deals",
        subtitle: "Share offers and explore exciting shopping deals.",
        style: "banner-one"
    },
    {
        title: "Shop Smart. Share More.",
        subtitle: "Explore exciting shopping offers.",
        style: "banner-two"
    },
    {
        title: "Your Earning Journey Starts Here",
        subtitle: "Discover deals across popular stores.",
        style: "banner-three"
    }
];

function getFirstName() {
    const savedName = sessionStorage.getItem("zaylopeUserName");

    if (savedName && savedName.trim()) {
        return savedName.trim();
    }

    const savedEmail = sessionStorage.getItem("zaylopeEmail");

    if (savedEmail && savedEmail.includes("@")) {
        const emailName = savedEmail.split("@")[0].split(/[._-]/)[0];
        return emailName.charAt(0).toUpperCase() + emailName.slice(1);
    }

    return "User";
}

export default function Dashboard() {
    const [activeMenu, setActiveMenu] = useState("Home");
    const [bannerIndex, setBannerIndex] = useState(0);
    const [menuOpen, setMenuOpen] = useState(false);
    const [membershipStatus, setMembershipStatus] = useState(null);
    const [sessionUser, setSessionUser] = useState(null);
    const [copiedLink, setCopiedLink] = useState(false);
    const [copiedId, setCopiedId] = useState(false);
    const menuToggle = useRef(null);
    const navigate = useNavigate();

    useEffect(() => {
        let active = true;
        API.get('/auth/session')
            .then((res) => {
                if (!active) return;
                const s = res.data;
                if (!s?.authenticated) {
                    navigate('/login', { replace: true });
                    return;
                }
                if (!s.canAccessDashboard) {
                    navigate('/register', { replace: true });
                    return;
                }
                setSessionUser(s);
                return API.get('/applicants/me/membership/status')
                    .then((mRes) => {
                        if (active) setMembershipStatus(mRes.data);
                    })
                    .catch(() => {});
            })
            .catch(() => {
                if (active) navigate('/login', { replace: true });
            });
        return () => {
            active = false;
        };
    }, [navigate]);

    useEffect(() => {
        const desktop = window.matchMedia("(min-width: 1024px)");
        const resetMenu = () => setMenuOpen(false);
        desktop.addEventListener("change", resetMenu);
        return () => desktop.removeEventListener("change", resetMenu);
    }, []);

    const userName = sessionUser?.fullName?.trim() || getFirstName();
    const userInitial = userName.charAt(0).toUpperCase();

    const handleLogout = async () => {
        try {
            await API.post('/auth/logout');
        } catch {
            // Clear UI state regardless
        }
        sessionStorage.removeItem("zaylopeToken");
        sessionStorage.removeItem("zaylopeUserName");
        sessionStorage.removeItem("zaylopeEmail");

        navigate("/login", { replace: true });
    };

    const changeBanner = (direction) => {
        setBannerIndex((current) =>
            (current + direction + banners.length) % banners.length
        );
    };

    const openMenu = (name) => {
        setActiveMenu(name);
        if (menuOpen) {
            setMenuOpen(false);
            menuToggle.current?.focus();
        }
        const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        window.scrollTo({ top: 0, behavior: reduceMotion ? "instant" : "smooth" });
    };

    return (
        <div className="z-dashboard">

            {/* LEFT SIDEBAR COLUMN */}
            <div className="z-sidebar-column" onKeyDown={event => {
                if (event.key === "Escape" && menuOpen) {
                    setMenuOpen(false);
                    menuToggle.current?.focus();
                }
            }}>

                {/* BRAND ABOVE SIDEBAR */}
                <div className="z-sidebar-brand">
                    <Link to="/" className="z-brand">
                        <span className="z-brand-icon">Z</span>
                        <span>Zaylo<span>Pe</span></span>
                    </Link>
                    <button type="button" className="z-sidebar-toggle" ref={menuToggle}
                        aria-expanded={menuOpen} aria-controls="dashboard-menu"
                        onClick={() => setMenuOpen(open => !open)}>
                        {menuOpen ? "Close menu" : "Menu"}
                    </button>
                </div>

                {/* SIDEBAR */}
                <aside className="z-sidebar" id="dashboard-menu" data-open={menuOpen}>

                    {/* USER PROFILE */}
                    <div className="z-profile-card">
                        <div className="z-profile-avatar">
                            {userInitial}
                        </div>

                        <p>Welcome back</p>
                        <h4>{userName}</h4>
                    </div>

                    {/* NAVIGATION */}
                    <nav className="z-side-menu" aria-label="Dashboard">
                        {menuItems.map((item) => (
                            <button
                                key={item.name}
                                aria-current={activeMenu === item.name ? "page" : undefined}
                                type="button"
                                className={`z-menu-item ${
                                    activeMenu === item.name ? "selected" : ""
                                }`}
                                onClick={() => openMenu(item.name)}
                            >
                                <span className="z-menu-icon" aria-hidden="true">
                                    {item.icon}
                                </span>

                                <span>{item.name}</span>
                            </button>
                        ))}
                    </nav>

                    {/* LOGOUT */}
                    <button
                        type="button"
                        className="z-logout"
                        onClick={handleLogout}
                    >
                        <span>↪</span> Logout
                    </button>

                </aside>
            </div>

            {/* MAIN AREA */}
            <div className="z-main-area">

                {/* TOP NAVIGATION BAR */}
                <header className="z-topbar">

                    <div className="z-search">
                        <span>⌕</span>
                        <input
                            type="text"
                            placeholder="Search for stores"
                            aria-label="Search for stores"
                        />
                    </div>

                    <div className="z-top-actions">

                        <button
                            type="button"
                            className="z-addon-button"
                        >
                            <span>⊕</span> Add Our Add-On
                        </button>

                        <button
                            type="button"
                            className="z-earnings-button"
                            onClick={() => openMenu("My Earnings")}
                        >
                            <span>₹</span>
                            Earnings ₹0.00
                            <span>⌄</span>
                        </button>

                        <button
                            type="button"
                            className="z-notification"
                            aria-label="Notifications"
                        >
                            ♧
                            <span className="z-notification-count">0</span>
                        </button>

                        <button
                            type="button"
                            className="z-top-profile"
                            aria-label={`My Account, ${userName}`}
                            onClick={() => openMenu("My Account")}
                        >
                            <span className="z-small-avatar">
                                {userInitial}
                            </span>
                            <span className="z-profile-name">{userName}</span>
                            <span aria-hidden="true">⌄</span>
                        </button>

                    </div>
                </header>

                {/* PAGE CONTENT */}
                <main className="z-content">

                    {activeMenu === "Home" ? (
                        <>
                            {/* AFFILIATE CREDENTIALS & REFERRAL CARD */}
                            {membershipStatus?.affiliateId ? (
                                <section className="z-affiliate-hero-card">
                                    <div className="z-affiliate-hero-header">
                                        <div className="z-affiliate-title-group">
                                            <span className="z-affiliate-badge">Lifetime Member</span>
                                            <h2>Official Membership Overview</h2>
                                        </div>
                                        <div className="z-affiliate-id-box">
                                            <span className="z-affiliate-id-label">Affiliate ID</span>
                                            <div className="z-affiliate-id-row">
                                                <code className="z-affiliate-id-value">{membershipStatus.affiliateId}</code>
                                                <button
                                                    type="button"
                                                    className="z-copy-btn"
                                                    onClick={() => {
                                                        navigator.clipboard?.writeText(membershipStatus.affiliateId);
                                                        setCopiedId(true);
                                                        setTimeout(() => setCopiedId(false), 2000);
                                                    }}
                                                >
                                                    {copiedId ? "Copied!" : "Copy"}
                                                </button>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="z-referral-link-section">
                                        <div className="z-referral-link-info">
                                            <label htmlFor="ref-url">Your Unique Referral Link</label>
                                            <div className="z-referral-input-row">
                                                <input
                                                    id="ref-url"
                                                    type="text"
                                                    readOnly
                                                    value={membershipStatus.referralUrl || `${window.location.origin}/register?ref=${membershipStatus.referralCode || ""}`}
                                                    className="z-referral-url-input"
                                                />
                                                <button
                                                    type="button"
                                                    className="z-copy-referral-btn"
                                                    onClick={() => {
                                                        const url = membershipStatus.referralUrl || `${window.location.origin}/register?ref=${membershipStatus.referralCode || ""}`;
                                                        navigator.clipboard?.writeText(url);
                                                        setCopiedLink(true);
                                                        setTimeout(() => setCopiedLink(false), 2000);
                                                    }}
                                                >
                                                    {copiedLink ? "Copied!" : "Copy Link"}
                                                </button>
                                            </div>
                                        </div>

                                        <div className="z-share-actions">
                                            <a
                                                href={`https://api.whatsapp.com/send?text=${encodeURIComponent(
                                                    `Join ZayloPe with my referral link and earn commissions: ${
                                                        membershipStatus.referralUrl || `${window.location.origin}/register?ref=${membershipStatus.referralCode || ""}`
                                                    }`
                                                )}`}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="z-share-btn z-whatsapp"
                                            >
                                                Share on WhatsApp
                                            </a>
                                            <a
                                                href={`https://t.me/share/url?url=${encodeURIComponent(
                                                    membershipStatus.referralUrl || `${window.location.origin}/register?ref=${membershipStatus.referralCode || ""}`
                                                )}&text=${encodeURIComponent("Join ZayloPe Affiliate Program")}`}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                                className="z-share-btn z-telegram"
                                            >
                                                Share on Telegram
                                            </a>
                                        </div>
                                    </div>
                                </section>
                            ) : membershipStatus && membershipStatus.paymentStatus !== "PAID" ? (
                                <section className="z-onboarding-alert-card">
                                    <div className="z-alert-icon">💳</div>
                                    <div className="z-alert-content">
                                        <h3>Complete Your ₹599 Affiliate Activation</h3>
                                        <p>
                                            Your onboarding application has been created. Complete your one-time ₹599 membership payment
                                            to unlock your Affiliate ID, referral links, and lifetime partner earnings.
                                        </p>
                                        <Link to="/register?step=3" className="z-alert-action-btn">
                                            Complete ₹599 Payment
                                        </Link>
                                    </div>
                                </section>
                            ) : membershipStatus && membershipStatus.paymentStatus === "PAID" ? (
                                <section className="z-onboarding-alert-card zp-pending-review">
                                    <div className="z-alert-icon">⏳</div>
                                    <div className="z-alert-content">
                                        <h3>Payment Confirmed — Completing Membership Activation</h3>
                                        <p>
                                            Your payment is confirmed. We&apos;re completing your membership activation and
                                            issuing your permanent Affiliate ID (ZP-YYMMDD-SSS).
                                        </p>
                                        <Link to="/register?step=4" className="z-alert-action-btn">
                                            Complete Activation
                                        </Link>
                                    </div>
                                </section>
                            ) : null}

                            {/* PROMOTIONAL BANNER */}
                            <section className="z-banner-section">

                                <button
                                    type="button"
                                    className="z-banner-arrow left"
                                    onClick={() => changeBanner(-1)}
                                    aria-label="Previous banner"
                                >
                                    ‹
                                </button>

                                <div className={`z-banner ${banners[bannerIndex].style}`}>
                                    <div className="z-banner-content">

                                        <span className="z-banner-tag">
                                            ZAYLOPE OFFERS
                                        </span>

                                        <h1>{banners[bannerIndex].title}</h1>

                                        <p>{banners[bannerIndex].subtitle}</p>

                                        <button
                                            type="button"
                                            onClick={() => openMenu("Stores")}
                                        >
                                            Explore Stores
                                        </button>

                                    </div>

                                    <div className="z-banner-art">
                                        <span>🛍️</span>
                                        <span>₹</span>
                                        <span>🎁</span>
                                    </div>
                                </div>

                                <button
                                    type="button"
                                    className="z-banner-arrow right"
                                    onClick={() => changeBanner(1)}
                                    aria-label="Next banner"
                                >
                                    ›
                                </button>

                                <div className="z-banner-dots">
                                    {banners.map((banner, index) => (
                                        <button
                                            type="button"
                                            key={banner.title}
                                            aria-label={`Show banner ${index + 1}`}
                                            aria-pressed={bannerIndex === index}
                                            className={bannerIndex === index ? "active" : ""}
                                            onClick={() => setBannerIndex(index)}
                                        />
                                    ))}
                                </div>

                            </section>

                            {/* TUTORIAL CARDS */}
                            <section className="z-tutorial-section">
                                <div className="z-horizontal-cards">
                                    {tutorials.map((item) => (
                                        <button
                                            type="button"
                                            className="z-tutorial-card"
                                            key={item.title}
                                            onClick={() => openMenu("My Account")}
                                        >
                                            <div className={`z-tutorial-image ${item.style}`}>
                                                <span className="z-tutorial-symbol">▶</span>
                                                <h3>{item.title}</h3>
                                            </div>

                                            <p>{item.subtitle}</p>
                                        </button>
                                    ))}
                                </div>
                            </section>

                            {/* TRENDING STORES */}
                            <section className="z-section">

                                <div className="z-section-heading">
                                    <h2>Trending Stores</h2>

                                    <button
                                        type="button"
                                        onClick={() => openMenu("Stores")}
                                    >
                                        View All ›
                                    </button>
                                </div>

                                <div className="z-store-grid">
                                    {stores.map((store) => (
                                        <div className="z-store-card" key={store.name}>

                                            <div className={`z-store-logo ${store.style}`}>
                                                {store.logo}
                                            </div>

                                            <h4>{store.name}</h4>
                                            <p>Earn through offers</p>

                                            <div className="z-store-actions">
                                                <button
                                                    type="button"
                                                    onClick={() => openMenu("Stores")}
                                                >
                                                    Share
                                                </button>

                                                <button
                                                    type="button"
                                                    className="z-share-icon"
                                                    onClick={() => openMenu("Stores")}
                                                    aria-label={`More about ${store.name}`}
                                                >
                                                    ↗
                                                </button>
                                            </div>

                                        </div>
                                    ))}
                                </div>

                            </section>

                            {/* EARNING ALERTS */}
                            <section className="z-earning-alert">

                                <div className="z-alert-icon">🔔</div>

                                <div className="z-alert-content">
                                    <h2>Earning Alerts</h2>

                                    <p>
                                        Your latest earning updates and
                                        shopping offers will appear here.
                                    </p>
                                </div>

                                <button
                                    type="button"
                                    onClick={() => openMenu("My Earnings")}
                                >
                                    View Updates
                                </button>

                            </section>

                            


{/* SALE CALENDAR */}
<section className="z-sale-section">
    <h2 className="z-sale-heading">Sale Calendar</h2>

    <div className="z-sale-table">
        <div className="z-sale-table-header">
            <div>Brand</div>
            <div>Sale Date</div>
        </div>

        {stores.map((store) => (
            <div className="z-sale-row" key={store.name}>
                <div className="z-sale-brand">
                    <div className={`z-sale-logo ${store.style}`}>
                        {store.logo}
                    </div>

                    <div className="z-sale-details">
                        <h4>
                            {store.name} {store.name === "Myntra"
                                ? "Grand Wedding Sale 💗"
                                : store.name === "AJIO"
                                ? "Clearance Sale 🛍️"
                                : store.name === "Dot & Key"
                                ? "Glow Sale ✨"
                                : "Shopping Sale ✨"}
                        </h4>

                        <p>
                            {store.name}
                            <span className="z-live-dot"></span>
                            <span className="z-live-text">Live</span>
                        </p>
                    </div>
                </div>

                <div className="z-sale-date">
                    <span className="z-live-dot"></span>
                    <span className="z-live-text">Live</span>
                </div>
            </div>
        ))}
    </div>
</section>

{/* FLASH DEALS */}
<section className="z-section z-flash-section">
    <div className="z-section-heading">
        <h2>Flash Deals</h2>
        <button type="button" onClick={() => openMenu("Flash Deals")}>View All →</button>
    </div>

    <div className="z-flash-grid">
        <div className="z-flash-card">
            <div className="z-flash-icon">⚡</div>
            <h3>Today's Best Deals</h3>
            <p>Discover exciting offers from popular stores.</p>
            <span>Explore Deals →</span>
        </div>

        <div className="z-flash-card">
            <div className="z-flash-icon">🔥</div>
            <h3>Trending Offers</h3>
            <p>Explore popular shopping offers and discounts.</p>
            <span>Explore Offers →</span>
        </div>

        <div className="z-flash-card">
            <div className="z-flash-icon">🛍️</div>
            <h3>Popular Stores</h3>
            <p>Find offers from your favourite shopping brands.</p>
            <span>Explore Stores →</span>
        </div>
    </div>
                        </section>

{/* MOST SHARED DEALS */}
<section className="z-section z-most-shared-section">
    <div className="z-section-heading">
        <h2>Most Shared Deals</h2>
        <button type="button" onClick={() => openMenu("Most Shared Deals")}>View All →</button>
    </div>

    <div className="z-shared-grid">
        <div className="z-shared-card">
            <div className="z-shared-image">
                <span>🛍️</span>
                <small>Popular</small>
            </div>
            <div className="z-shared-info">
                <h3>Popular Shopping Deals</h3>
                <p>Amazon</p>
                <div className="z-shared-bottom">
                    <span>Most Shared</span>
                    <strong>↗ Popular</strong>
                </div>
            </div>
        </div>

        <div className="z-shared-card">
            <div className="z-shared-image">
                <span>👗</span>
                <small>Trending</small>
            </div>
            <div className="z-shared-info">
                <h3>Fashion & Lifestyle Deals</h3>
                <p>Myntra</p>
                <div className="z-shared-bottom">
                    <span>Most Shared</span>
                    <strong>↗ Popular</strong>
                </div>
            </div>
        </div>

        <div className="z-shared-card">
            <div className="z-shared-image">
                <span>✨</span>
                <small>Popular</small>
            </div>
            <div className="z-shared-info">
                <h3>Beauty & Lifestyle Offers</h3>
                <p>AJIO</p>
                <div className="z-shared-bottom">
                    <span>Most Shared</span>
                    <strong>↗ Popular</strong>
                </div>
            </div>
        </div>
    </div>
</section>

                        </>
                    ) : (

                        <section className="z-other-page">

                            <h2>{activeMenu}</h2>

                            <p>Welcome to ZAYLOPE {activeMenu}.</p>

                            <div className="z-other-placeholder">
                                <span>📊</span>
                                <h3>{activeMenu}</h3>

                                <p>
                                    This section is ready for its
                                    functionality to be connected.
                                </p>
                            </div>

                        </section>

                    )}

                    {/* FOOTER */}
                    <footer className="z-dashboard-footer">
                        © 2026 ZAYLOPE. All Rights Reserved.
                    </footer>

                </main>

            </div>
        </div>
    );
}
