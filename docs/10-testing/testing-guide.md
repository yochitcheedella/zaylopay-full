# Testing Guide

From `frontend/`, install with `npm ci`, then run:

```powershell
npm run lint
npm run build
node --test src/utils/validation.test.mjs qa/messages-unit.mjs qa/login-validation-unit.mjs qa/identity-validation-unit.mjs qa/auth-login-cooldown-unit.mjs qa/footer-unit.mjs qa/runtime-unit.mjs
```

There is no `npm test` script. The identity unit suite reads the retained backend domain fixture: use the complete checkout. The prior handover run passed all 64 Node tests, lint and build. These do not establish browser correctness.

With the frontend on localhost:5173 and Chromium installed, run `node qa/identity-validation-check.cjs`, `node qa/auth-login-cooldown-check.cjs` and `node qa/footer-check.cjs`. The prior run passed 9 identity checks, 7 cooldown scenarios and 10 footer groups with zero scoped axe violations. See [QA prerequisites](../../frontend/qa/README.md). Capture/preview/debug tools are private and are not dependencies of retained tests.

Backend targeted tests, from `backend/`:

```powershell
.\mvnw.cmd "-Dtest=BankEncryptionServiceTest,ApplicantSessionPropertiesTest,EmailAdmissionBoundaryTest,ApplicantRegistrationWriterTest" test
```

Full suites require an isolated MySQL server. Inspect `test-otp.ps1`: it owns port 3308, refuses an occupied port and generates private credentials. Older auth/membership/admin suites additionally need `TEST_DB_HOST=127.0.0.1`, `TEST_DB_PORT=3308`, `TEST_DB_USERNAME` and `TEST_DB_PASSWORD` matching that isolated server; the script does not set those variables for you. Never run their defaults against a shared/application server or substitute application schemas. No schema/record changes are authorized by test failures.

The prior controlled `mvnw.cmd -o verify` run used explicit isolated settings: 425 tests, 407 passed, 3 failures, 12 errors, 3 skipped. See [known issues](../12-project-status/known-issues.md). Offline `-o` assumes cached dependencies. Bank keys in the test profile protect synthetic data only; real startup requires the existing bank key externally. No real payment or OTP delivery is needed for these tests.
