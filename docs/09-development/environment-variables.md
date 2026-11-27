# Environment Variables

From `.env.example` and `application.properties`:

| Variable | Purpose | Required (Prod) | Safe Example |
| --- | --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`dev` / `prod`) | Yes | `prod` |
| `DB_URL` | MySQL JDBC URL | Yes | `jdbc:mysql://host:3306/zaylope?sslMode=REQUIRED` |
| `DB_USERNAME` / `DB_PASSWORD` | MySQL credentials | Yes | `<db-user>` / `<db-password>` |
| `BANK_ENCRYPTION_KEY` | AES-256-GCM key for bank numbers | Yes | `<32+-byte-random-secret>` |
| `OTP_HASH_KEY` | Keyed hash secret for OTPs | Yes | `<32+-byte-random-secret>` |
| `APPLICANT_SESSION_SECURE_COOKIE` | `Secure` flag on session cookies | Yes (`true`) | `true` |
| `APP_CORS_ALLOWED_ORIGIN` | Allowed frontend origin | Yes | `https://app.zaylope.com` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google OAuth2 credentials | Yes | `<google-client-id>` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Email OTP SMTP config | Yes | `smtp.example.com`, `587` |
| `MOBILE_OTP_ENABLED`, `EMAIL_OTP_ENABLED` | Enable OTP channels | Yes | `true` |
