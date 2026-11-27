# Local Setup

## Prerequisites
- Java 21 (JDK 21+), Node.js 22.13+ or newer compatible release, npm, MySQL 8.x.

## Steps (Windows PowerShell)
1. **Create MySQL schema**: Create an empty `zaylope` database (never use `zaylo_db`).
2. **Start Backend (`:8080`)**:
   ```powershell
   $env:SPRING_PROFILES_ACTIVE = "dev"
   $env:DB_URL = "jdbc:mysql://127.0.0.1:3306/zaylope?allowPublicKeyRetrieval=true&sslMode=DISABLED"
   $env:DB_USERNAME = "<user>"
   $env:DB_PASSWORD = "<password>"
   $env:BANK_ENCRYPTION_KEY = "<existing-key-or-new-isolated-installation-secret>"
   cd backend
   .\mvnw.cmd spring-boot:run
   ```
3. **Start Frontend (`:5173`)**:
   ```powershell
   cd frontend
npm ci
   npm run dev
   ```
