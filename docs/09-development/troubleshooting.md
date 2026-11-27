# Troubleshooting

- **`ApplicantSessionProperties: No default constructor found`**: Resolved by `@ConstructorBinding` on the canonical compact constructor in `ApplicantSessionProperties.java`.
- **`mvnw.cmd` hangs in restricted environments**: Use `.\mvnw.cmd -o` (offline mode) when dependencies are already cached in `~/.m2`.
- **`InvalidRegistrationInput: This email provider is not supported`**: `EmailContactPolicy` enforces the configured domain allowlist; use a supported provider or configure `EmailPolicyProperties` in tests.
