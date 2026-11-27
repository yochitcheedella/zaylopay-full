# Production Checklist

- [ ] `SPRING_PROFILES_ACTIVE=prod` set (disables mock OTP delivery, enables `secureCookie=true`).
- [ ] `BANK_ENCRYPTION_KEY` externalized and hardcoded fallback removed from `BankEncryptionService.java` / `application.properties`.
- [ ] `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` configured with TLS.
- [ ] `OTP_HASH_KEY`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, Razorpay keys, and SMTP credentials configured.
- [ ] `APP_CORS_ALLOWED_ORIGIN` set to production frontend origin.
- [ ] Flyway migrations `V1`–`V13` validated; automated MySQL backups enabled.
