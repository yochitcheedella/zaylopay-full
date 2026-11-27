/**
 * Zero-dependency PDF 1.4 generator for ZayloPe membership payment receipts.
 * Produces a standards-compliant PDF document matching the View Receipt data.
 */

function escapePdfText(value) {
  return String(value ?? '')
    .replace(/\\/g, '\\\\')
    .replace(/\(/g, '\\(')
    .replace(/\)/g, '\\)')
    .replace(/[^\x20-\x7E]/g, ' ');
}

function formatDate(isoString) {
  if (!isoString) return '—';
  try {
    return new Date(isoString).toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  } catch {
    return '—';
  }
}

export function getReceiptNumber(membership = {}) {
  return membership?.paymentId || membership?.orderId || '';
}

export function getReceiptFilename(membership = {}) {
  const raw = getReceiptNumber(membership);
  const safe = String(raw).replace(/[^a-zA-Z0-9_-]/g, '') || 'Verified';
  return `ZayloPe-Receipt-${safe}.pdf`;
}

export function buildReceiptPdfBytes(membership = {}) {
  const receiptNumber = getReceiptNumber(membership);
  if (!receiptNumber) {
    throw new Error('Verified payment details are required to generate a receipt.');
  }

  const amountPaise = Number(membership?.amountPaise || 59900);
  const currency = membership?.currency || 'INR';
  const formattedFee = `${currency} ${Math.round(amountPaise / 100)}`;
  const memberName = (membership?.applicantName || '').trim() || 'ZayloPe Member';
  const affiliateId = membership?.affiliateId || '—';
  const activatedAt = formatDate(membership?.activatedAt);

  const lines = [
    { text: 'ZAYLOPE', font: 'F2', size: 20, y: 770 },
    { text: 'Membership Payment Receipt', font: 'F2', size: 14, y: 744 },
    { text: '------------------------------------------------------------------------', font: 'F1', size: 10, y: 726 },
    { text: `Receipt Number:   ${receiptNumber}`, font: 'F1', size: 11, y: 702 },
    { text: 'Receipt Status:   Verified & Captured', font: 'F2', size: 11, y: 682 },
    { text: `Member Name:      ${memberName}`, font: 'F1', size: 11, y: 662 },
    { text: `Affiliate ID:     ${affiliateId}`, font: 'F1', size: 11, y: 642 },
    { text: `Activated On:     ${activatedAt}`, font: 'F1', size: 11, y: 622 },
  ];

  let nextY = 602;
  if (membership?.orderId) {
    lines.push({ text: `Order Reference:  ${membership.orderId}`, font: 'F1', size: 11, y: nextY });
    nextY -= 20;
  }
  if (membership?.paymentId) {
    lines.push({ text: `Payment ID:       ${membership.paymentId}`, font: 'F1', size: 11, y: nextY });
    nextY -= 20;
  }
  lines.push({ text: `Amount Paid (incl. 18% GST): ${formattedFee}`, font: 'F2', size: 12, y: nextY - 6 });
  lines.push({
    text: '------------------------------------------------------------------------',
    font: 'F1',
    size: 10,
    y: nextY - 28,
  });
  lines.push({
    text: 'This is a system-generated receipt for your verified ZayloPe membership.',
    font: 'F1',
    size: 9,
    y: nextY - 46,
  });

  const streamBody = lines
    .map((line) => `BT /${line.font} ${line.size} Tf 56 ${line.y} Td (${escapePdfText(line.text)}) Tj ET`)
    .join('\n');

  const objects = [
    '1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n',
    '2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n',
    '3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n',
    '4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n',
    '5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n',
    `6 0 obj\n<< /Length ${streamBody.length} >>\nstream\n${streamBody}\nendstream\nendobj\n`,
  ];

  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  for (const obj of objects) {
    offsets.push(pdf.length);
    pdf += obj;
  }

  const xrefOffset = pdf.length;
  pdf += `xref\n0 ${objects.length + 1}\n`;
  pdf += '0000000000 65535 f \n';
  for (let i = 1; i < offsets.length; i++) {
    pdf += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;

  return new TextEncoder().encode(pdf);
}

export async function downloadReceiptPdf(membership) {
  const bytes = buildReceiptPdfBytes(membership);
  const blob = new Blob([bytes], { type: 'application/pdf' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = getReceiptFilename(membership);
  link.rel = 'noopener';
  document.body.appendChild(link);
  try {
    link.click();
  } finally {
    document.body.removeChild(link);
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
