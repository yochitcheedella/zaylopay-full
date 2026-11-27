# Secondary Modules (Documents, Bank Details, Admin)

- **Document Upload (`document/`)**: `ApplicantDocumentController.java` handles multipart document uploads after mobile+email verification and consent (`V5`). Not active in the primary 3-step registration UI.
- **Bank Details (`applicant/`)**: `ApplicantBankDetailsController.java` and `BankEncryptionService.java` encrypt account numbers using AES-256-GCM. See [Known Issues](../12-project-status/known-issues.md) regarding `BANK_ENCRYPTION_KEY` fallback.
- **Admin (`admin/`)**: `AdminController.java` and `AdminDashboard.jsx` (`/admin`, `/admin/approvals`) provide filtered applicant listing and review actions.
