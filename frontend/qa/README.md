# Frontend QA

Reusable browser checks remain at their existing paths so backend test invocations and imports do not change. Required fixtures remain with the tests or backend validation resources; historical HTML/SVG design previews are private. Generated output goes to `artifacts/` (ignored), or the private directory selected by `ZAYLOPE_QA_ARTIFACTS`. Older suites may still write ignored screenshots beside QA source.

`support/runtime.cjs` resolves a local Chromium installation, or uses `CHROME_PATH` / `BROWSER_EXECUTABLE_PATH`. It never downloads a browser. Install a compatible browser separately if none is available. Dependencies come from this frontend's `package-lock.json`, not personal agent-tool directories.

Run from `frontend/` with the app on `http://localhost:5173`:

```powershell
node --test src/utils/validation.test.mjs qa/messages-unit.mjs qa/login-validation-unit.mjs qa/identity-validation-unit.mjs qa/auth-login-cooldown-unit.mjs qa/footer-unit.mjs qa/runtime-unit.mjs
node qa/earn-integration-check.cjs
node qa/footer-check.cjs
node qa/identity-validation-check.cjs
node qa/auth-login-cooldown-check.cjs
```

The identity/cooldown UI suites intercept controlled API fixtures and send no real codes. `auth-login-live.cjs` is launched by the isolated MySQL Java test; it requires a private fixture OTP handoff and must not be run against real contacts. It retains its own Vite port 5180 and test-specific API origin.

One-time capture, baseline, preview and debugging scripts are excluded from the development baseline. Reusable assertions, accessibility checks, the performance observation, runtime helper, Node tests and isolated live browser adapters remain at their existing paths. Some retained older registration/OTP suites may expect historical contracts: inspect prerequisites before use; do not restore removed business stages to satisfy obsolete assertions.

All prior screenshots, videos and generated reports were checksum-archived in the private cleanup workspace. New output is disposable evidence, not source fixtures or customer data. Review artifacts for sensitive content before sharing. Do not run source-mutating one-time helper scripts; those have been preserved in the private historical archive.
