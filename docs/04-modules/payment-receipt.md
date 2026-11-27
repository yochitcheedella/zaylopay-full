# Payment Receipt Module (View & Download PDF)

- **Overview & Purpose**: Allows authenticated members with a verified payment (`paymentId` or `orderId`) to view their receipt inline and download it as a PDF (`BR-07`, `FR-08`).
- **View Receipt (Existing)**:
  - `MembershipSuccess.jsx` toggles `showReceipt` via **View payment receipt** / **Hide receipt** button, rendering `.zp-ms-receipt-box` with Receipt Status (`Verified & Captured`), Order Reference, Payment ID, and Amount Paid (incl. 18% GST).
  - `MemberWelcomePage.jsx` also provides an inline `Payment receipt` drawer.
- **Download Receipt (New)**:
  - `frontend/src/utils/receiptPdf.js` (`buildReceiptPdfBytes`, `getReceiptFilename`, `downloadReceiptPdf`) generates a standards-compliant PDF 1.4 document client-side from verified `membership` state.
  - Filename: `ZayloPe-Receipt-{receiptNumber}.pdf`.
  - Downloads directly via Blob URL without opening a new tab or print dialog; includes loading state (`Preparing PDF...`), duplicate-click prevention, and inline error + **Retry** button.
- **Verification Status**: **Implemented** (`npm run lint` 0 errors, `npm run build` passed, Node PDF-structure check passed; live browser E2E download unverified).
