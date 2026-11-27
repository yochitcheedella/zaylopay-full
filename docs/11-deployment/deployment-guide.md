# Deployment Guide

1. **Frontend Build**: `cd frontend && npm ci && npm run build` → deploy `frontend/dist/` behind HTTPS.
2. **Backend Package**: `cd backend && .\mvnw.cmd clean package` → run JAR with `SPRING_PROFILES_ACTIVE=prod`.
3. **Database**: Ensure MySQL 8 `zaylope` schema is reachable over TLS; Flyway (`V1`–`V13`) validates on startup with `ddl-auto=none`.
