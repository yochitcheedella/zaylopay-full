# ZayloPe Registration & Membership Portal

ZayloPe is a web application for onboarding applicants, verifying mobile and email contacts via OTP or Google Sign-In, collecting membership payment (₹599 INR via Razorpay), and issuing permanent Affiliate IDs with downloadable payment receipts.

## Current Application Scope

- **Public Website**: Home, About, How It Works, and Membership overview pages.
- **Applicant Registration & Onboarding**: 3-step registration flow (Details → Contact Verification → Membership Payment & Success).
- **Authentication & Sessions**: Mobile/Email OTP login, Google OAuth2 sign-in/linking, and HTTP-only cookie-backed `ApplicantSession` management.
- **Membership & Payments**: Razorpay order creation, signature verification, webhook handling, automatic Affiliate ID (`ZP-YYMMDD-XXX`) generation on captured payment, inline **View payment receipt**, and **Download Receipt** PDF export.
- **Additional Implemented Modules**: Document upload (`ApplicantDocumentController`), encrypted bank details (`ApplicantBankDetailsController` / `BankEncryptionService`), and admin review endpoints (`AdminController` / `AdminDashboard`).

## Technology Stack

| Layer | Technology | Version (from project) |
| --- | --- | --- |
| Frontend | React, Vite, React Router DOM | `19.3.1`, `8.3.6`, `7.18.5` |
| Styling & Icons | Tailwind CSS (`@tailwindcss/vite`), Lucide React | `4.3.5`, `1.77.0` |
| HTTP & Auth | Axios, `@react-oauth/google` | `1.21.1`, `0.13.5` |
| Backend | Java, Spring Boot, Spring Data JPA | `21`, `4.1.2` |
| Database & Migrations | MySQL, Flyway (`V1`–`V13`) | MySQL 8, Flyway (Spring Boot managed) |
| PDF & Validation | Apache PDFBox (backend), custom PDF 1.4 utility (frontend), `oxlint` | `3.0.8`, `1.90.0` |

## High-Level Architecture

```
Browser (React SPA :5173) ──HTTPS/JSON + Session Cookie──▶ Spring Boot API (:8080) ──JPA/Flyway──▶ MySQL (zaylope)
                                                               │
                                                               ├──▶ Razorpay API / Webhooks
                                                               ├──▶ Google OAuth2 Token Info
                                                               └──▶ SMTP / Console OTP Adapter
```

## Repository Structure

- `frontend/` — React + Vite SPA (`src/Pages`, `src/Components`, `src/Services`, `src/utils`)
- `backend/` — Spring Boot 4.1.2 / Java 21 API (`src/main/java/com/zaylope/registration/*`, `src/main/resources/db/migration/*`)
- `docs/` — Complete project documentation (`01-project/` through `12-project-status/`)
- `.env.example` — Environment variable template for backend configuration

## Prerequisites

- **Java 21** (JDK 21+)
- **Node.js 22.13+** (or a newer supported release satisfying lockfile engines) and **npm**
- **MySQL 8.x** with an isolated `zaylope` schema

## Quick Start

### 1. Database & Environment

Create a dedicated MySQL database `zaylope` (do not point at any unrelated populated schema) and set required environment variables (see [docs/09-development/environment-variables.md](docs/09-development/environment-variables.md) and `.env.example`):

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:mysql://127.0.0.1:3306/zaylope?allowPublicKeyRetrieval=true&sslMode=DISABLED"
$env:DB_USERNAME = "<your-mysql-user>"
$env:DB_PASSWORD = "<your-mysql-password>"
$env:BANK_ENCRYPTION_KEY = "<existing-key-or-new-isolated-installation-secret>"
```

### 2. Backend (`:8080`)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

### 3. Frontend (`:5173`)

```powershell
cd frontend
npm ci
npm run dev
```

## Testing & Build Commands

- **Frontend lint & build**:
  ```powershell
  cd frontend
  npm run lint
  npm run build
  ```
- **Backend targeted & full tests**:
  ```powershell
  cd backend
  .\mvnw.cmd "-Dtest=EmailAdmissionBoundaryTest" test
  # Full MySQL suites require explicit isolated-server settings; see docs/10-testing/testing-guide.md.
  ```

## Current Limitations & Known Blockers

- **Development baseline only**: the complete isolated backend run had 407 passes, 3 failures, 12 errors and 3 skips. Auth, membership and OTP integration failures remain open; see [known issues](docs/12-project-status/known-issues.md), including the Guava advisory.
- **Bank key required externally**: runtime defaults have been removed. Existing installations must retain their exact current `BANK_ENCRYPTION_KEY`; do not rotate it or re-encrypt records to make startup pass. Spring Boot does not automatically load the root environment example.
- **Receipt PDF**: Generated client-side in `frontend/src/utils/receiptPdf.js` from verified membership state; no server-side PDF endpoint exists yet.

## Documentation Navigation

Start at [docs/README.md](docs/README.md) for the complete documentation index and role-based reading paths.
