# System Architecture

ZayloPe is a two-tier web application consisting of a React 19 SPA (`frontend/`, port `5173`) and a Spring Boot 4.1.2 REST API (`backend/`, port `8080`) backed by MySQL 8 (`zaylope` schema).

```mermaid
flowchart LR
    User["User Browser"] -->|HTTPS / JSON + HttpOnly Cookies| FE["React 19 + Vite SPA (:5173)"]
    FE -->|REST API /api/v1/*| BE["Spring Boot 4.1.2 API (:8080)"]
    BE -->|Spring Data JPA + Flyway| DB["MySQL 8 (zaylope)"]
    BE -->|Order & Signature Verify| RZP["Razorpay API"]
    RZP -->|Webhook /api/v1/webhooks/razorpay| BE
    BE -->|ID Token Verify| GOOG["Google OAuth2"]
    BE -->|SMTP / Dev Mock| OTP["OTP Delivery"]
```

## Trust Boundaries
- **Browser ↔ Backend**: Authenticated via HTTP-only cookies (`ZAYLOPE_APPLICANT`, `ZAYLOPE_ONBOARDING`) validated by `ApplicantSessionFilter`.
- **Payment Verification**: Never trusted from client-only callbacks; verified server-side via Razorpay HMAC signature and webhook handlers.
- **Database**: `spring.jpa.hibernate.ddl-auto=none`; schema evolution is strictly managed via Flyway (`V1`–`V13`).
