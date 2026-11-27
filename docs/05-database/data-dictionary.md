# Data Dictionary

Authoritative summary of application-owned tables from Flyway `V1`–`V13`:

| Table | Purpose | Key Columns & Sensitivity |
| --- | --- | --- |
| `applicants` | Core applicant/member record | `applicant_id` (PK), `full_name`, `mobile_number` (PII, UK), `email` (PII, UK), `active_email_key`, `pending_mobile_key`, `google_sub` (UK), `affiliate_id` (UK), `payment_status`, `approval_status`, `encrypted_bank_account` (AES-256-GCM) |
| `applicant_registration_locks` | Pessimistic lock table for mobile reservations | `mobile_number` (PK), `applicant_id`, `expires_at` |
| `applicant_sessions` | HTTP-only session tokens | `session_id` (PK), `applicant_id` (FK), `token_hash` (SHA-256), `scope` (`ONBOARDING`/`AUTHENTICATED`), `expires_at` |
| `applicant_verification_codes` | Contact OTP challenges | `id` (PK), `applicant_id` (FK), `channel` (`MOBILE`/`EMAIL`), `code_hash`, `expires_at` |
| `applicant_login_challenges` | Login OTP challenges | `id` (PK), `applicant_id` (FK), `code_hash`, `expires_at` |
| `applicant_documents` | Uploaded identity documents | `id` (PK), `applicant_id` (FK), `document_type`, `storage_path` |
| `payments` | Razorpay order & payment records | `id` (PK), `applicant_id` (FK), `razorpay_order_id` (UK), `razorpay_payment_id` (UK), `amount_paise`, `status` |
