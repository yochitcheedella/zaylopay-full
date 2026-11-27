# Coding Standards

- **Frontend**: PascalCase React components (`MembershipSuccess.jsx`), camelCase functions/variables, `oxlint` (`npm run lint`) with 0 warnings/errors.
- **Backend**: Java 21 records for DTOs/properties, constructor injection, `@Transactional` only at service/writer boundaries (keep DNS/HTTP calls outside transactions), `GlobalExceptionHandler` for uniform API errors.
