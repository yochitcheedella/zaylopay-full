# Database Migrations

Flyway is enabled (`spring.flyway.enabled=true`, `validate-on-migrate=true`, `baseline-on-migrate=true`, `baseline-version=9`) with migrations in `backend/src/main/resources/db/migration/`:

- `V1__create_applicants.sql`: Base `applicants` table.
- `V2`–`V3`: `applicant_sessions`, mobile verification, pending mobile reservation invariant.
- `V4`–`V5`: `applicant_login_challenges`, `applicant_documents`, submission state.
- `V6`–`V9`: Unique email/mobile constraints, `applicant_verification_codes`, OTP hardening.
- `V10`: Payments, approvals, and `affiliate_id` columns.
- `V11`–`V13`: Google OAuth2 `google_sub`, auth/session hardening, case-sensitive email local-part preservation.

> [!CAUTION]
> Never set `spring.jpa.hibernate.ddl-auto` to `create` or `update`, never replay migrations, and never point at any unrelated populated `zaylo_db` schema.
