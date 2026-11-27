# Registration Module

- **Overview & Purpose**: Captures applicant full name, Indian mobile (`+91`), and email address; creates a draft applicant and issues an `ONBOARDING` session (`BR-02`, `FR-02`).
- **Frontend**: `frontend/src/Pages/Register/Register.jsx`, `RegistrationStepper.jsx`, `FormControls.jsx`.
- **Backend**: `ApplicantController.java` (`POST /api/v1/applicants/register`), `ApplicantServiceImpl.java`, `ApplicantRegistrationWriter.java`, `EmailContactPolicy.java`, `ApplicantRepository.java`, `ApplicantRegistrationLockRepository.java`.
- **Database**: `applicants`, `applicant_registration_locks`.
- **Workflow**:
  ```mermaid
  sequenceDiagram
      participant U as Applicant
      participant FE as Register.jsx
      participant Svc as ApplicantServiceImpl
      participant W as ApplicantRegistrationWriter
      participant DB as MySQL
      U->>FE: Submit name, mobile, email
      FE->>Svc: POST /api/v1/applicants/register
      Svc->>Svc: EmailContactPolicy + DNS preflight (outside TX)
      Svc->>W: register(request) [@Transactional]
      W->>W: emailContactPolicy.normalizeNewContact()
      W->>DB: Pessimistic lock on mobile & email keys
      W->>DB: Save Applicant + Lock
      W-->>FE: 201 Created + ZAYLOPE_ONBOARDING cookie
  ```
- **Test Evidence**: `EmailAdmissionBoundaryTest` (4/4 passed), `ApplicantRegistrationWriterTest` (6/6 passed).
- **Status**: **Implemented and verified**.
