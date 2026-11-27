import React, { useState, useEffect, useCallback } from 'react';
import API from '../../Services/api';
import './AdminDashboard.css';

export default function AdminDashboard() {
  const [admin, setAdmin] = useState(null);
  const [checkingAuth, setCheckingAuth] = useState(true);

  // Login form state
  const [loginForm, setLoginForm] = useState({ username: 'admin', password: '' });
  const [loginLoading, setLoginLoading] = useState(false);
  const [loginError, setLoginError] = useState('');

  // Dashboard state
  const [applicants, setApplicants] = useState([]);
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [searchQuery, setSearchQuery] = useState('');
  const [loadingList, setLoadingList] = useState(false);
  const [selectedApplicant, setSelectedApplicant] = useState(null);
  const [loadingDetails, setLoadingDetails] = useState(false);

  // Approval / Rejection modal state
  const [actionModal, setActionModal] = useState(null); // { type: 'APPROVE' | 'REJECT', applicant: ... }
  const [actionNotes, setActionNotes] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState('');
  const [approvalResult, setApprovalResult] = useState(null);

  // Document preview state
  const [previewDoc, setPreviewDoc] = useState(null);

  // 1. Check current admin session
  useEffect(() => {
    let ignore = false;
    API.get('/admin/auth/me')
      .then(res => {
        if (!ignore) setAdmin(res.data);
      })
      .catch(() => {
        if (!ignore) setAdmin(null);
      })
      .finally(() => {
        if (!ignore) setCheckingAuth(false);
      });
    return () => { ignore = true; };
  }, []);

  // 2. Fetch applicants list
  const fetchApplicants = useCallback(async () => {
    if (!admin) return;
    setLoadingList(true);
    try {
      const params = {};
      if (statusFilter !== 'ALL') params.status = statusFilter;
      if (searchQuery.trim()) params.q = searchQuery.trim();

      const res = await API.get('/admin/applicants', { params });
      setApplicants(Array.isArray(res.data) ? res.data : []);
    } catch {
      // Handled silently
    } finally {
      setLoadingList(false);
    }
  }, [admin, statusFilter, searchQuery]);

  useEffect(() => {
    if (!admin) return;
    let ignore = false;
    const params = {};
    if (statusFilter !== 'ALL') params.status = statusFilter;
    if (searchQuery.trim()) params.q = searchQuery.trim();

    API.get('/admin/applicants', { params })
      .then(res => {
        if (!ignore) {
          setApplicants(Array.isArray(res.data) ? res.data : []);
          setLoadingList(false);
        }
      })
      .catch(() => {
        if (!ignore) setLoadingList(false);
      });

    return () => { ignore = true; };
  }, [admin, statusFilter, searchQuery]);

  // 3. Admin Login
  const handleLoginSubmit = async (e) => {
    e.preventDefault();
    setLoginLoading(true);
    setLoginError('');
    try {
      const res = await API.post('/admin/auth/login', {
        username: loginForm.username.trim(),
        password: loginForm.password,
      });
      setAdmin(res.data);
      setLoginForm({ username: '', password: '' });
    } catch (err) {
      setLoginError(err.response?.data?.detail || 'Invalid username or password.');
    } finally {
      setLoginLoading(false);
    }
  };

  // 4. Admin Logout
  const handleLogout = async () => {
    try {
      await API.post('/admin/auth/logout', {});
    } catch {
      // Ignore
    }
    setAdmin(null);
    setSelectedApplicant(null);
  };

  // 5. Open Applicant Details
  const handleViewApplicant = async (app) => {
    setLoadingDetails(true);
    setSelectedApplicant(null);
    try {
      const res = await API.get(`/admin/applicants/${app.applicantId}`);
      setSelectedApplicant(res.data);
    } catch {
      // Fallback to summary
      setSelectedApplicant(app);
    } finally {
      setLoadingDetails(false);
    }
  };

  // 6. Handle Approve Action
  const handleConfirmApprove = async () => {
    if (!actionModal?.applicant) return;
    setActionLoading(true);
    setActionError('');
    try {
      const res = await API.post(`/admin/applicants/${actionModal.applicant.applicantId}/approve`, {
        notes: actionNotes.trim(),
      });
      setApprovalResult(res.data);
      fetchApplicants();
      // Update selected applicant view if open
      if (selectedApplicant?.applicantId === actionModal.applicant.applicantId) {
        handleViewApplicant(actionModal.applicant);
      }
    } catch (err) {
      setActionError(err.response?.data?.detail || 'Failed to approve application.');
    } finally {
      setActionLoading(false);
    }
  };

  // 7. Handle Reject Action
  const handleConfirmReject = async () => {
    if (!actionModal?.applicant) return;
    if (!actionNotes.trim()) {
      setActionError('Please specify a rejection reason.');
      return;
    }
    setActionLoading(true);
    setActionError('');
    try {
      await API.post(`/admin/applicants/${actionModal.applicant.applicantId}/reject`, {
        reason: actionNotes.trim(),
      });
      setActionModal(null);
      fetchApplicants();
      if (selectedApplicant?.applicantId === actionModal.applicant.applicantId) {
        handleViewApplicant(actionModal.applicant);
      }
    } catch (err) {
      setActionError(err.response?.data?.detail || 'Failed to reject application.');
    } finally {
      setActionLoading(false);
    }
  };

  // Calculate stats
  const totalCount = applicants.length;
  const pendingCount = applicants.filter((a) => a.approvalStatus === 'PENDING').length;
  const approvedCount = applicants.filter((a) => a.approvalStatus === 'APPROVED').length;
  const rejectedCount = applicants.filter((a) => a.approvalStatus === 'REJECTED').length;

  if (checkingAuth) {
    return (
      <div className="zp-admin-loading-screen">
        <div className="zp-admin-spinner" />
        <p>Verifying admin permissions...</p>
      </div>
    );
  }

  // Render Login Screen if unauthenticated
  if (!admin) {
    return (
      <div className="zp-admin-login-wrapper">
        <div className="zp-admin-login-card">
          <div className="zp-admin-brand">
            <span className="zp-admin-logo">ZayloPe</span>
            <span className="zp-admin-tag">Admin Portal</span>
          </div>

          <h2 className="zp-admin-login-title">Administrator Sign In</h2>
          <p className="zp-admin-login-desc">
            Sign in to review applicant submissions, verify payment reconciliation, and approve Affiliate IDs.
          </p>

          {loginError && <div className="zp-admin-alert-error">{loginError}</div>}

          <form onSubmit={handleLoginSubmit} className="zp-admin-login-form">
            <div className="zp-admin-field">
              <label htmlFor="admin-username">Username</label>
              <input
                id="admin-username"
                type="text"
                value={loginForm.username}
                onChange={(e) => setLoginForm({ ...loginForm, username: e.target.value })}
                placeholder="e.g. admin"
                required
                autoComplete="username"
              />
            </div>

            <div className="zp-admin-field">
              <label htmlFor="admin-password">Password</label>
              <input
                id="admin-password"
                type="password"
                value={loginForm.password}
                onChange={(e) => setLoginForm({ ...loginForm, password: e.target.value })}
                placeholder="Enter password (default: admin123)"
                required
                autoComplete="current-password"
              />
            </div>

            <button type="submit" disabled={loginLoading} className="zp-admin-btn-primary">
              {loginLoading ? 'Signing in...' : 'Sign In as Administrator'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // Render Admin Approval Dashboard
  return (
    <div className="zp-admin-app">
      {/* Admin Header */}
      <header className="zp-admin-header">
        <div className="zp-admin-header-content">
          <div className="zp-admin-brand-group">
            <span className="zp-admin-logo">ZayloPe</span>
            <span className="zp-admin-header-pill">Compliance &amp; Approvals</span>
          </div>
          <div className="zp-admin-user-group">
            <div className="zp-admin-avatar">{admin.username.charAt(0).toUpperCase()}</div>
            <div className="zp-admin-user-info">
              <span className="zp-admin-name">{admin.fullName || admin.username}</span>
              <span className="zp-admin-role">{admin.role}</span>
            </div>
            <button onClick={handleLogout} className="zp-admin-logout-btn">
              Sign Out
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="zp-admin-main">
        {/* Metrics Bar */}
        <section className="zp-admin-metrics-row">
          <div className="zp-metric-card">
            <span className="zp-metric-label">Total Applications</span>
            <span className="zp-metric-val">{totalCount}</span>
          </div>
          <div className="zp-metric-card zp-metric-pending">
            <span className="zp-metric-label">Pending Approval</span>
            <span className="zp-metric-val">{pendingCount}</span>
          </div>
          <div className="zp-metric-card zp-metric-approved">
            <span className="zp-metric-label">Approved Affiliates</span>
            <span className="zp-metric-val">{approvedCount}</span>
          </div>
          <div className="zp-metric-card zp-metric-rejected">
            <span className="zp-metric-label">Rejected</span>
            <span className="zp-metric-val">{rejectedCount}</span>
          </div>
        </section>

        {/* Filters & Search */}
        <section className="zp-admin-controls-card">
          <div className="zp-filter-tabs">
            {['ALL', 'PENDING', 'APPROVED', 'REJECTED'].map((tab) => (
              <button
                key={tab}
                type="button"
                className={`zp-filter-tab ${statusFilter === tab ? 'is-active' : ''}`}
                onClick={() => setStatusFilter(tab)}
              >
                {tab === 'ALL' ? 'All Applications' : tab.charAt(0) + tab.slice(1).toLowerCase()}
              </button>
            ))}
          </div>

          <div className="zp-admin-search-wrap">
            <input
              type="text"
              placeholder="Search by name, mobile, email, or Affiliate ID..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="zp-admin-search-input"
            />
            {searchQuery && (
              <button
                type="button"
                className="zp-clear-search-btn"
                onClick={() => setSearchQuery('')}
              >
                ✕
              </button>
            )}
          </div>
        </section>

        {/* Applications Table */}
        <section className="zp-admin-table-card">
          {loadingList ? (
            <div className="zp-table-loading">Loading applications...</div>
          ) : applicants.length === 0 ? (
            <div className="zp-table-empty">No applications found matching your criteria.</div>
          ) : (
            <div className="zp-table-responsive">
              <table className="zp-admin-table">
                <thead>
                  <tr>
                    <th>Applicant / Ref</th>
                    <th>Full Legal Name</th>
                    <th>Contact Details</th>
                    <th>Docs</th>
                    <th>₹599 Payment</th>
                    <th>Approval Status</th>
                    <th>Affiliate ID</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {applicants.map((app) => (
                    <tr key={app.applicantId}>
                      <td>
                        <span className="zp-cell-ref">{app.registrationId || `APP-${app.applicantId}`}</span>
                      </td>
                      <td>
                        <strong className="zp-cell-name">{app.fullName}</strong>
                      </td>
                      <td>
                        <div className="zp-cell-contacts">
                          <span>{app.mobileNumber}</span>
                          <small>{app.email}</small>
                        </div>
                      </td>
                      <td>
                        <span className="zp-badge-docs">{app.documentsCount} Uploaded</span>
                      </td>
                      <td>
                        {app.paymentStatus === 'PAID' ? (
                          <span className="zp-badge-paid">₹599 Paid ✓</span>
                        ) : (
                          <span className="zp-badge-unpaid">Pending</span>
                        )}
                      </td>
                      <td>
                        <span className={`zp-status-badge is-${(app.approvalStatus || 'pending').toLowerCase()}`}>
                          {app.approvalStatus || 'PENDING'}
                        </span>
                      </td>
                      <td>
                        {app.affiliateId ? (
                          <code className="zp-affiliate-id-pill">{app.affiliateId}</code>
                        ) : (
                          <span className="zp-muted-text">—</span>
                        )}
                      </td>
                      <td>
                        <button
                          type="button"
                          className="zp-action-view-btn"
                          onClick={() => handleViewApplicant(app)}
                        >
                          Review
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>

      {/* Applicant Detail Drawer / Modal */}
      {selectedApplicant && (
        <div className="zp-drawer-overlay" onClick={() => setSelectedApplicant(null)}>
          <div className="zp-drawer-content" onClick={(e) => e.stopPropagation()}>
            <div className="zp-drawer-header">
              <div>
                <h3 className="zp-drawer-title">{selectedApplicant.fullName}</h3>
                <span className="zp-drawer-ref">
                  Reference: {selectedApplicant.registrationId || `APP-${selectedApplicant.applicantId}`}
                </span>
              </div>
              <button
                type="button"
                className="zp-close-drawer-btn"
                onClick={() => setSelectedApplicant(null)}
              >
                ✕
              </button>
            </div>

            <div className="zp-drawer-body">
              {loadingDetails ? (
                <div className="zp-table-loading">Loading details...</div>
              ) : (
                <>
                  {/* Status Banner */}
                  <div className="zp-drawer-status-bar">
                    <div>
                      <span className="zp-meta-label">Payment Status</span>
                      <strong className={selectedApplicant.paymentStatus === 'PAID' ? 'zp-text-paid' : 'zp-text-unpaid'}>
                        {selectedApplicant.paymentStatus === 'PAID' ? '₹599 Paid & Confirmed' : 'Payment Incomplete'}
                      </strong>
                    </div>
                    <div>
                      <span className="zp-meta-label">Approval Status</span>
                      <strong className={`zp-status-text is-${(selectedApplicant.approvalStatus || 'pending').toLowerCase()}`}>
                        {selectedApplicant.approvalStatus || 'PENDING'}
                      </strong>
                    </div>
                    {selectedApplicant.affiliateId && (
                      <div>
                        <span className="zp-meta-label">Affiliate ID</span>
                        <code className="zp-affiliate-id-pill">{selectedApplicant.affiliateId}</code>
                      </div>
                    )}
                  </div>

                  {/* Contact Info */}
                  <div className="zp-drawer-section">
                    <h4 className="zp-section-title">Contact Verification</h4>
                    <div className="zp-detail-grid">
                      <div>
                        <span className="zp-detail-label">Mobile Number</span>
                        <span className="zp-detail-val">
                          {selectedApplicant.mobileNumber} {selectedApplicant.mobileVerified && '✓'}
                        </span>
                      </div>
                      <div>
                        <span className="zp-detail-label">Email Address</span>
                        <span className="zp-detail-val">
                          {selectedApplicant.email} {selectedApplicant.emailVerified && '✓'}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Bank Details */}
                  <div className="zp-drawer-section">
                    <h4 className="zp-section-title">Bank Payout Details</h4>
                    {selectedApplicant.bankDetails ? (
                      <div className="zp-detail-grid">
                        <div>
                          <span className="zp-detail-label">Account Holder</span>
                          <span className="zp-detail-val">{selectedApplicant.bankDetails.accountHolderName}</span>
                        </div>
                        <div>
                          <span className="zp-detail-label">Bank Name</span>
                          <span className="zp-detail-val">{selectedApplicant.bankDetails.bankName}</span>
                        </div>
                        <div>
                          <span className="zp-detail-label">Account Number</span>
                          <span className="zp-detail-val">{selectedApplicant.bankDetails.maskedAccountNumber}</span>
                        </div>
                        <div>
                          <span className="zp-detail-label">IFSC Code</span>
                          <span className="zp-detail-val">{selectedApplicant.bankDetails.ifscCode}</span>
                        </div>
                      </div>
                    ) : (
                      <p className="zp-empty-note">Bank details not yet submitted.</p>
                    )}
                  </div>

                  {/* Uploaded Documents */}
                  <div className="zp-drawer-section">
                    <h4 className="zp-section-title">Uploaded Identity Documents</h4>
                    {selectedApplicant.documents && selectedApplicant.documents.length > 0 ? (
                      <div className="zp-doc-list">
                        {selectedApplicant.documents.map((d) => (
                          <div key={d.documentId} className="zp-doc-card">
                            <div>
                              <strong className="zp-doc-type">{d.documentType}</strong>
                              <span className="zp-doc-name">{d.originalFilename}</span>
                            </div>
                            <button
                              type="button"
                              className="zp-doc-preview-btn"
                              onClick={async () => {
                                try {
                                  const res = await API.get(`/applicants/me/documents/${d.documentId}/content`, {
                                    responseType: 'blob',
                                  });
                                  const url = URL.createObjectURL(res.data);
                                  setPreviewDoc({ url, filename: d.originalFilename });
                                } catch {
                                  alert('Unable to load document preview.');
                                }
                              }}
                            >
                              Preview
                            </button>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="zp-empty-note">No documents uploaded.</p>
                    )}
                  </div>

                  {/* Payment Transactions */}
                  <div className="zp-drawer-section">
                    <h4 className="zp-section-title">Payment Transactions</h4>
                    {selectedApplicant.payments && selectedApplicant.payments.length > 0 ? (
                      <div className="zp-tx-list">
                        {selectedApplicant.payments.map((p, idx) => (
                          <div key={idx} className="zp-tx-card">
                            <div>
                              <span className="zp-tx-order">Order: {p.gatewayOrderId}</span>
                              {p.gatewayPaymentId && <small>Payment: {p.gatewayPaymentId}</small>}
                            </div>
                            <div className="zp-tx-status-col">
                              <strong>₹{(p.amountPaise / 100).toFixed(0)}</strong>
                              <span className={`zp-tx-pill is-${p.status.toLowerCase()}`}>{p.status}</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="zp-empty-note">No payment attempts recorded.</p>
                    )}
                  </div>

                  {/* Approval Actions */}
                  <div className="zp-drawer-actions">
                    <button
                      type="button"
                      disabled={selectedApplicant.paymentStatus !== 'PAID' || selectedApplicant.approvalStatus === 'APPROVED'}
                      className="zp-btn-approve"
                      onClick={() => {
                        setActionModal({ type: 'APPROVE', applicant: selectedApplicant });
                        setActionNotes('');
                        setActionError('');
                        setApprovalResult(null);
                      }}
                    >
                      {selectedApplicant.approvalStatus === 'APPROVED' ? 'Already Approved' : 'Approve Application'}
                    </button>

                    <button
                      type="button"
                      disabled={selectedApplicant.approvalStatus === 'REJECTED'}
                      className="zp-btn-reject"
                      onClick={() => {
                        setActionModal({ type: 'REJECT', applicant: selectedApplicant });
                        setActionNotes('');
                        setActionError('');
                        setApprovalResult(null);
                      }}
                    >
                      {selectedApplicant.approvalStatus === 'REJECTED' ? 'Already Rejected' : 'Reject Application'}
                    </button>
                  </div>
                  {selectedApplicant.paymentStatus !== 'PAID' && (
                    <small className="zp-approval-locked-hint">
                      Approval is locked until ₹599 membership payment is confirmed.
                    </small>
                  )}
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Confirmation Modal for Approve / Reject */}
      {actionModal && (
        <div className="zp-modal-overlay">
          <div className="zp-action-modal-card">
            <h3 className="zp-modal-title">
              {actionModal.type === 'APPROVE' ? 'Approve Application' : 'Reject Application'}
            </h3>
            <p className="zp-modal-desc">
              {actionModal.type === 'APPROVE'
                ? `Approving ${actionModal.applicant.fullName} will automatically generate their unique Affiliate ID (ZP-YYMMDD-SSS) and activate their referral access.`
                : `Please provide a rejection reason for ${actionModal.applicant.fullName}.`}
            </p>

            {actionError && <div className="zp-admin-alert-error">{actionError}</div>}

            {approvalResult ? (
              <div className="zp-approval-success-result">
                <div className="zp-success-check">✓</div>
                <h4>Affiliate Approved Successfully!</h4>
                <div className="zp-issued-id-box">
                  <span>Assigned Affiliate ID:</span>
                  <code>{approvalResult.affiliateId}</code>
                </div>
                <div className="zp-issued-id-box">
                  <span>Referral Code:</span>
                  <code>{approvalResult.referralCode}</code>
                </div>
                <button
                  type="button"
                  className="zp-admin-btn-primary"
                  onClick={() => {
                    setActionModal(null);
                    setApprovalResult(null);
                  }}
                >
                  Close &amp; Return to List
                </button>
              </div>
            ) : (
              <>
                <div className="zp-admin-field">
                  <label htmlFor="action-notes">
                    {actionModal.type === 'APPROVE' ? 'Compliance Remarks (Optional)' : 'Rejection Reason (Required)'}
                  </label>
                  <textarea
                    id="action-notes"
                    rows={3}
                    value={actionNotes}
                    onChange={(e) => setActionNotes(e.target.value)}
                    placeholder={
                      actionModal.type === 'APPROVE'
                        ? 'e.g. Contact verification and ₹599 payment confirmed.'
                        : 'e.g. Invalid application details.'
                    }
                  />
                </div>

                <div className="zp-modal-actions">
                  <button
                    type="button"
                    disabled={actionLoading}
                    className="zp-admin-btn-secondary"
                    onClick={() => setActionModal(null)}
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    disabled={actionLoading}
                    className={actionModal.type === 'APPROVE' ? 'zp-btn-approve' : 'zp-btn-reject'}
                    onClick={actionModal.type === 'APPROVE' ? handleConfirmApprove : handleConfirmReject}
                  >
                    {actionLoading
                      ? 'Processing...'
                      : actionModal.type === 'APPROVE'
                      ? 'Confirm & Issue Affiliate ID'
                      : 'Confirm Rejection'}
                  </button>
                </div>
              </>
            )}
          </div>
        </div>
      )}

      {/* Document Preview Lightbox */}
      {previewDoc && (
        <div className="zp-preview-modal-overlay" onClick={() => setPreviewDoc(null)}>
          <div className="zp-preview-modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="zp-preview-modal-header">
              <span>{previewDoc.filename}</span>
              <button onClick={() => setPreviewDoc(null)}>✕</button>
            </div>
            <div className="zp-preview-modal-body">
              <iframe src={previewDoc.url} title="Document Preview" width="100%" height="500px" />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
