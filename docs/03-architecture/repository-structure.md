# Repository Structure

Publishable source structure (excluding `node_modules/`, `target/`, `dist/`, `.env`, and local AI workspace files):

```text
zaylope/
├── .env.example                    # Backend environment variable template
├── .gitignore                      # Git ignore rules
├── DESIGN.md                       # UI/UX design tokens and guidelines
├── README.md                       # Root project entry point
├── backend/
│   ├── mvnw, mvnw.cmd, .mvn/       # Maven Wrapper
│   ├── pom.xml                     # Spring Boot 4.1.2, Java 21, MySQL, Flyway, PDFBox
│   └── src/
│       ├── main/java/com/zaylope/registration/
│       │   ├── admin/              # Admin review controller & service
│       │   ├── affiliate/          # Affiliate ID generation & membership controller
│       │   ├── applicant/          # Applicant entity, repository, bank encryption
│       │   ├── auth/               # OTP login & Google OAuth2 controllers
│       │   ├── common/             # EmailContactPolicy, validators, exception handlers
│       │   ├── config/             # @ConfigurationProperties & security config
│       │   ├── document/           # Document upload controller & storage
│       │   ├── otp/                # OTP challenge & keyed-hash verification
│       │   ├── payment/            # Razorpay order, verify, and webhook controllers
│       │   ├── registration/       # Registration writer, locks, state, submission
│       │   ├── security/           # Security helpers & rate limiting
│       │   ├── session/            # HttpOnly cookie session filter & service
│       │   └── verification/       # Mobile/Email OTP verification endpoints
│       ├── main/resources/
│       │   ├── application*.properties
│       │   ├── db/migration/       # Flyway V1..V13 SQL migrations
│       │   └── validation/         # Disposable email domain list
│       └── test/java/              # Unit & MySQL integration tests
├── docs/                           # Structured documentation (01-project .. 12-project-status)
└── frontend/
    ├── package.json, package-lock.json
    ├── vite.config.js
    └── src/
        ├── App.jsx                 # SPA router
        ├── Components/             # Navbar, Footer, PublicLayout
        ├── Pages/                  # Home, About, Membership, Register, Login, Welcome, Admin
        ├── Services/               # Axios API clients
        ├── motion/                 # Reduced-motion provider
        └── utils/                  # receiptPdf.js (PDF 1.4 receipt generator)
```

Retained development inputs also include rontend/public/, rontend/qa/ reusable tests and runtime helper, rontend/scripts/audit-runner.js, environment examples, lint configuration, ackend/start-backend.cmd, ackend/test-otp.ps1, ackend/repair-otp-schema.ps1, test resources, and ackend/src/main/resources/db/manual/. One-time handover utilities, historical audit inventories, previews/captures, private AI instructions, build/dependency outputs and local database state are excluded. No company CI is configured; existing AI upgrade hooks are private.
