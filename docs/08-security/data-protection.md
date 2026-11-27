# Data Protection

- **Bank Account Encryption**: `BankEncryptionService.java` encrypts account numbers with AES-256-GCM (12-byte random IV + 128-bit tag, SHA-256 derived key) and masks display values (`•••• •••• •••• 1234`).
- **External key required**: runtime key defaults have been removed. Supply `BANK_ENCRYPTION_KEY` externally; existing installations must keep their exact existing key. The cipher and records are unchanged. Rotation requires a separate approved data/key migration; synthetic test keys must never be used for real data.
- **Logging**: Secrets, OTPs, and full bank account numbers must never be logged (`VerificationProperties.toString()` redacts `otpHashKey`).
