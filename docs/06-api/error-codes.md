# Error Codes

Handled centrally by `GlobalExceptionHandler.java`:

| HTTP Status | Code / Exception | Meaning & Frontend Handling |
| --- | --- | --- |
| `400` | `InvalidRegistrationInputException` | Field validation or `EmailContactPolicy` failure; display field-level error (`errors[0].field`). |
| `401` | Unauthorized / `InvalidOtpException` | Missing/expired session cookie or invalid OTP; prompt re-login or OTP retry. |
| `409` | `DuplicateEmailException` / `MobileNumberAlreadyUsedException` / `MobileAlreadyVerifiedException` | Account already exists or mobile/email reserved; direct user to `/login`. |
| `503` | `EMAIL_DOMAIN_CHECK_UNAVAILABLE` (`EmailDomainUnavailableException`) | Temporary DNS preflight failure; show retryable error. |
