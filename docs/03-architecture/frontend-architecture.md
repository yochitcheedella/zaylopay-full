# Frontend Architecture

- **Entry & Routing**: `frontend/src/App.jsx` configures `BrowserRouter` with `PublicLayout` (`/`, `/about`, `/how-it-works`, `/membership`) and lazy-loaded account routes (`/login`, `/register`, `/welcome`, `/dashboard`, `/admin`, `/admin/approvals`).
- **Pages (`frontend/src/Pages/`)**:
  - `Home/`, `About/`, `Howitworkspage/`, `Membership/`: Public marketing pages.
  - `Register/`: 3-step onboarding (`RegistrationStepper`, `MembershipSuccess`, `ApprovalStatus`, `FormControls`).
  - `Login/`: OTP and Google OAuth2 login.
  - `Welcome/`: `MemberWelcomePage.jsx` for authenticated members.
  - `Admin/`: `AdminDashboard.jsx`.
- **Services & Utilities**:
  - `frontend/src/Services/`: Axios API client with `withCredentials: true` for cookie sessions.
  - `frontend/src/utils/receiptPdf.js`: Zero-dependency PDF 1.4 generator for `Download Receipt`.
  - `frontend/src/motion/`: `MotionProvider` with reduced-motion support.
