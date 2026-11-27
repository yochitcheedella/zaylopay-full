# Known Issues

1. **Bank key required externally**: runtime defaults have been removed from `BankEncryptionService` and `application.properties`. Existing installations must supply their exact existing `BANK_ENCRYPTION_KEY`; do not rotate it to make startup succeed. AES-GCM and existing records are unchanged. Test-profile keys are synthetic, never for real bank data.
2. **MySQL Integration Test Failures**:
   - `MembershipActivationMySqlIntegrationTest` (3 errors): test email domains rejected by `EmailContactPolicy` allowlist.
   - `AuthMySqlIntegrationTest` (4 errors): 3 `InvalidRegistrationInput`, 1 `InvalidOtp`.
   - `OtpMySqlIntegrationTest`: 3 failures and 5 errors involving captured mock delivery, invalid OTP and expected provider-failure behavior.
   - Complete prior isolated run: **425 tests, 407 passed, 3 failures, 12 errors, 3 skipped**. Compile/package/isolated health success does not override these failures. The skipped schema/browser opt-ins must never be enabled against an application database just to obtain a green result.
3. **Resolved Issues**:
   - `ApplicantSessionProperties` `NoSuchMethodException` — **Resolved** via `@ConstructorBinding`.
   - `ApplicantRegistrationWriter` / `ApplicantRepository` compilation failures — **Resolved**.

4. **Dependency advisory**: OSV matched resolved Guava `33.1.0-jre` to [GHSA-xxph-c9ww-hj94](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94), concerning deserialization memory allocation (fixed in `33.7.2`). Application exploitability has not been established. Compatibility review, upgrade and regression testing remain pending. Frontend npm audit reported zero advisories during the prior handover review.
5. **Development configuration**: `prod` and `production` differ in secure-cookie defaults; explicitly select/configure the approved HTTPS profile. `dev` uses console OTP mocks and a development-only hash default; never use it with real contacts or in production. Supply a private `OTP_HASH_KEY` and real delivery providers. Logs and local database state remain private.
6. **Document storage policy**: development wording claims encryption at rest, but the local storage implementation writes bytes with filesystem controls. Storage-layer encryption and approved retention/access policies remain unverified.
7. **Receipt limits**: synthetic browser tests verified matching INR 599 receipt view/download and no extra payment requests on repeated downloads. Non-ASCII names are stripped and amounts rounded to whole rupees. Provider-backed capture and two-user HTTP receipt ownership remain unverified. Welcome has inline receipt view; PDF download is on registration success.
8. **Database/deployment**: Flyway baseline/rollout compatibility and backups need review before deployment. Never replay migrations or alter application records to make tests pass; `ddl-auto=none` is unchanged.
9. **Documentation**: some API/schema/acceptance summaries still need reconciliation with controllers and migrations. Historical decisions are preserved as history; implementation does not establish business approval. This repository is a development baseline, not a production release. See the [testing guide](../10-testing/testing-guide.md).
