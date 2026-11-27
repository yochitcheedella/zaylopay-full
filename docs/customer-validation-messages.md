# Customer validation messages

Implemented 26 November 2026.

Shared copy is in `frontend/src/utils/messages.js`. The Axios response interceptor in `Services/api.js` maps backend codes and field errors to local copy before registration, login, Google completion, contact changes, OTP, payment, document, member and admin consumers read them. Unknown server errors use safe retry guidance rather than backend exception text. Retry timestamps, status, session state and internal codes remain available for application behaviour. Customer copy does not expose those codes. Existing explicit UI messages remain where they already explain a particular action; this is not a claim that every string in the repository was moved.

The unsupported provider message is exactly: “Please use Gmail, Yahoo, Outlook or another supported email address.” Shared input validators use the catalogue. Known legacy backend provider/disposable field messages map to their new local wording. Domain suggestions remain explicit buttons: typing or blurring never replaces the customer's email. Existing field error clearing, error icons, accessible descriptions and form-level service outage handling are preserved.

Registration Continue and Google profile completion now require all required personal fields to pass the shared validation. Backend validation and authentication rules were not changed. Unknown login identities retain generic responses; the error mapper does not disclose account existence. Shared field feedback uses 14px Inter, 1.5 line height, dark red #b42318, a brief appearance transition and reduced-motion support. Existing field controls place errors below inputs with error icons. Genuine service/payment failures retain contextual feedback rather than being falsely assigned to an input.

Payment SDK failure descriptions and Google SDK exceptions are no longer displayed directly. Payment failure guidance asks customers to check payment status before retrying. API payment error mappings distinguish payment confirmation failure, an existing completed payment and an unavailable gateway.

## Modified and created files

- New: `frontend/src/utils/messages.js`, `frontend/qa/messages-unit.mjs`, this report.
- Modified: `frontend/src/utils/validation.js`, `frontend/src/Services/api.js`, `frontend/src/index.css`, `frontend/src/Pages/Register/Register.jsx`, `frontend/src/Pages/Register/components/PaymentCheckout.jsx`, `frontend/src/Components/Auth/GoogleAuthButton.jsx`.
- Updated QA expectations: `frontend/qa/identity-validation-unit.mjs`, `identity-validation-check.cjs`.
- Evidence: `frontend/qa/messages-unit.log`, `messages-browser-final.log`, `messages-login.log`, `messages-build.log`, `messages-before.png`, `messages-after.png`, `messages-after-375.png`, `messages-after-768.png`.

## Actual verification

- 47 Node tests passed, zero failures/skips: validation fixtures, routes, identity policy, cooldowns and five new message-safety checks. The new checks cover exact provider wording, arbitrary exception suppression, safe field messages, OTP/payment recovery instructions and preserved rate-limit metadata.
- Nine identity browser groups passed using intercepted fixtures: unsupported/typo/disposable/spoofed domains, explicit suggestion acceptance, Indian mobile input, temporary email-check outage, registration verification navigation, contact correction, policy outage/retry, Google profile validation and desktop/375px/768px layouts. The browser also asserts computed feedback font size 14px and colour rgb(180,35,24).
- Seven login browser groups passed: authoritative countdown, independent identifiers, IP limits, network feedback and clearing, explicit recovery, responsive layouts and unverified saved-session presentation.
- Frontend lint and production build passed. No backend source changed, database mutation or real OTP/payment submission was needed for this copy/error-handling task.

The before image is the preserved previously captured desktop provider-error state, not a recreated rendering. After images are newly captured browser output. Mobile after images show the same field-level message without horizontal overflow. No mobile before screenshot was available. External Google SDK failures and actual Razorpay failures were not exercised against live providers; their local callback changes were build-checked and payment/OTP message mapping was unit-tested. The browser suites use fixtures, not live payment credentials. This report does not claim exhaustive execution of every possible backend error code or every SDK failure state.

`frontend/qa/messages-before.png` (archived evidence) · `frontend/qa/messages-after.png` (archived evidence) · `frontend/qa/messages-after-375.png` (archived evidence) · `frontend/qa/messages-after-768.png` (archived evidence)


Repository cleanup note: the evidence paths marked above are preserved in the private verified archive, indexed by original path in `docs/testing/archived-files.csv`. They are not required runtime inputs.
