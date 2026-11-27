# Authentication & Session Management Module

- **Overview & Purpose**: Authenticates verified applicants/members via OTP login or Google OAuth2 and maintains HTTP-only cookie sessions (`BR-04`, `FR-04`, `FR-05`).
- **Frontend**: `frontend/src/Pages/Login/Login.jsx`, `@react-oauth/google`.
- **Backend**: `ApplicantLoginController.java`, `GoogleAuthController.java`, `ApplicantSessionService.java`, `ApplicantSessionFilter.java`, `ApplicantSessionProperties.java`.
- **Database**: `applicants` (`google_sub`, `email_verified_at`, `mobile_verified`), `applicant_sessions`, `applicant_login_challenges`.
- **Test Evidence**: `ApplicantSessionPropertiesTest` passed; `AuthMySqlIntegrationTest` has 4 failing cases in current run (see [Known Issues](../12-project-status/known-issues.md)).
- **Status**: **Implemented** (MySQL integration tests require remediation).
