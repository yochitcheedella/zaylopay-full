import { useCallback, useEffect, useRef, useState } from "react";
import API from "../../Services/api";
import "./DocumentUploadPanel.css";

const MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB
const ALLOWED_EXTENSIONS = ["jpg", "jpeg", "png", "pdf"];

// UI Upload State Machine
const UploadState = {
    EMPTY: "EMPTY",
    SELECTED: "SELECTED",
    VALIDATING: "VALIDATING",
    UPLOADING: "UPLOADING",
    UPLOADED: "UPLOADED",
    PENDING_REVIEW: "PENDING_REVIEW",
    APPROVED: "APPROVED",
    REJECTED: "REJECTED",
    UPLOAD_FAILED: "UPLOAD_FAILED",
};

export default function DocumentUploadPanel({ onDocumentsChange, disabled }) {
    // Policy & configuration from backend
    const [policy, setPolicy] = useState(null);
    const [policyLoading, setPolicyLoading] = useState(true);

    // Document lists & state
    const [documents, setDocuments] = useState([]);
    const [selectedType, setSelectedType] = useState("");
    const [selectedFile, setSelectedFile] = useState(null);
    const [consentChecked, setConsentChecked] = useState(false);
    const [uploadState, setUploadState] = useState(UploadState.EMPTY);
    const [uploadProgress, setUploadProgress] = useState(0);

    // Messages & Modals
    const [error, setError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    const [isDragActive, setIsDragActive] = useState(false);
    const [previewModal, setPreviewModal] = useState(null); // { url, type, title }

    const fileInputRef = useRef(null);
    const localPreviewUrlRef = useRef(null);

    // Revoke any temporary object URL when unmounting or changing preview
    const cleanupLocalPreview = useCallback(() => {
        if (localPreviewUrlRef.current) {
            URL.revokeObjectURL(localPreviewUrlRef.current);
            localPreviewUrlRef.current = null;
        }
    }, []);

    useEffect(() => {
        return () => {
            cleanupLocalPreview();
        };
    }, [cleanupLocalPreview]);

    // Load document policy & existing applicant documents on mount
    useEffect(() => {
        let isMounted = true;
        async function fetchPolicyAndDocuments() {
            setPolicyLoading(true);
            setError("");
            try {
                const [policyRes, docsRes] = await Promise.all([
                    API.get("/applicants/me/documents/policy"),
                    API.get("/applicants/me/documents"),
                ]);
                if (!isMounted) return;

                setPolicy(policyRes.data);
                const fetchedDocs = Array.isArray(docsRes.data) ? docsRes.data : [];
                setDocuments(fetchedDocs);

                if (policyRes.data?.documentTypes?.length > 0) {
                    const uploadedTypes = new Set(fetchedDocs.map((d) => d.documentType.toLowerCase()));
                    const pendingRequired = policyRes.data.documentTypes.find(
                        (dt) => dt.required && !uploadedTypes.has(dt.type.toLowerCase())
                    );
                    setSelectedType(pendingRequired ? pendingRequired.type : policyRes.data.documentTypes[0].type);
                }
            } catch (err) {
                if (!isMounted) return;
                setError(
                    err.response?.data?.detail ||
                    "Failed to load document verification policy. Please check your session and reload."
                );
            } finally {
                if (isMounted) setPolicyLoading(false);
            }
        }
        fetchPolicyAndDocuments();
        return () => {
            isMounted = false;
        };
    }, []);

    // Notify parent component whether all required documents are uploaded
    useEffect(() => {
        if (!policy) return;
        const uploadedTypes = new Set(documents.map((d) => d.documentType.toLowerCase()));
        const requiredTypes = (policy.documentTypes || []).filter((dt) => dt.required);
        const allSatisfied = requiredTypes.every((req) => uploadedTypes.has(req.type.toLowerCase()));
        onDocumentsChange?.(documents, allSatisfied);
    }, [documents, policy, onDocumentsChange]);

    const isAadhaarType = (type) => {
        return /aadhaar/i.test(type || "");
    };

    // Client-side file validation
    const validateFile = (file) => {
        if (!file) {
            return "No file selected.";
        }
        if (file.size === 0) {
            return "The selected file is empty. Please choose a valid document.";
        }
        if (file.size > MAX_FILE_SIZE_BYTES) {
            return "File size exceeds the 10 MB maximum limit. Please upload a smaller file.";
        }

        const extension = file.name.split(".").pop()?.toLowerCase() || "";
        if (!ALLOWED_EXTENSIONS.includes(extension)) {
            return "Unsupported file format. Please upload JPG, JPEG, PNG, or PDF.";
        }

        return null;
    };

    const handleFileSelection = (file) => {
        setError("");
        setSuccessMessage("");
        cleanupLocalPreview();

        if (!file) {
            setSelectedFile(null);
            setUploadState(UploadState.EMPTY);
            return;
        }

        setUploadState(UploadState.VALIDATING);
        const validationError = validateFile(file);
        if (validationError) {
            setError(validationError);
            setSelectedFile(null);
            setUploadState(UploadState.UPLOAD_FAILED);
            if (fileInputRef.current) fileInputRef.current.value = "";
            return;
        }

        setSelectedFile(file);
        setUploadState(UploadState.SELECTED);

        // Create object URL for local image preview
        if (file.type.startsWith("image/")) {
            localPreviewUrlRef.current = URL.createObjectURL(file);
        }
    };

    const onDrop = (e) => {
        e.preventDefault();
        setIsDragActive(false);
        if (disabled || uploadState === UploadState.UPLOADING) return;

        if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
            handleFileSelection(e.dataTransfer.files[0]);
        }
    };

    const onDragOver = (e) => {
        e.preventDefault();
        if (!disabled && uploadState !== UploadState.UPLOADING) {
            setIsDragActive(true);
        }
    };

    const onDragLeave = () => {
        setIsDragActive(false);
    };

    const handleUpload = async () => {
        setError("");
        setSuccessMessage("");

        if (!selectedType) {
            setError("Please select a document type.");
            return;
        }

        if (!selectedFile) {
            setError("Please select a document file to upload.");
            return;
        }

        if (isAadhaarType(selectedType) && !consentChecked) {
            setError("Please confirm consent for masked Aadhaar upload before continuing.");
            return;
        }

        const formData = new FormData();
        formData.append("file", selectedFile);

        setUploadState(UploadState.UPLOADING);
        setUploadProgress(0);

        try {
            const endpoint = `/applicants/me/documents/${encodeURIComponent(selectedType)}?consent=true`;
            const response = await API.put(endpoint, formData, {
                headers: {
                    "Content-Type": "multipart/form-data",
                },
                onUploadProgress: (progressEvent) => {
                    if (progressEvent.total) {
                        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total);
                        setUploadProgress(percent);
                    }
                },
            });

            // Update document list with new or replaced document
            setDocuments((prev) => {
                const filtered = prev.filter((d) => d.documentType.toLowerCase() !== selectedType.toLowerCase());
                return [...filtered, response.data];
            });

            setSuccessMessage(`${selectedType} uploaded successfully and queued for verification.`);
            setUploadState(UploadState.UPLOADED);
            setSelectedFile(null);
            cleanupLocalPreview();
            if (fileInputRef.current) fileInputRef.current.value = "";
        } catch (err) {
            setUploadState(UploadState.UPLOAD_FAILED);
            const detail = err.response?.data?.detail;
            const code = err.response?.data?.code;

            if (code === "DOCUMENT_CONSENT_REQUIRED") {
                setError("Document privacy consent is required before uploading.");
            } else if (err.response?.status === 413) {
                setError("The file exceeds the 10 MB maximum upload limit.");
            } else if (code === "INVALID_DOCUMENT") {
                setError(detail || "Invalid document file. Please ensure it is an unencrypted, valid PDF, PNG, or JPG.");
            } else {
                setError(detail || "Failed to upload document. Please retry or contact support.");
            }
        } finally {
            setUploadProgress(0);
        }
    };

    const handleRemoveDocument = async (doc) => {
        if (!window.confirm(`Are you sure you want to remove your ${doc.documentType}?`)) {
            return;
        }

        setError("");
        setSuccessMessage("");
        try {
            await API.delete(`/applicants/me/documents/${encodeURIComponent(doc.documentId)}`);
            setDocuments((prev) => prev.filter((d) => d.documentId !== doc.documentId));
            setSuccessMessage(`${doc.documentType} removed successfully.`);
        } catch (err) {
            setError(err.response?.data?.detail || "Unable to remove document. Please retry.");
        }
    };

    const handleStartReplace = (doc) => {
        setSelectedType(doc.documentType);
        setSelectedFile(null);
        cleanupLocalPreview();
        setUploadState(UploadState.EMPTY);
        setError("");
        setSuccessMessage("");
        if (fileInputRef.current) {
            fileInputRef.current.value = "";
            fileInputRef.current.click();
        }
    };

    const handleViewDocument = async (doc) => {
        try {
            const response = await API.get(`/applicants/me/documents/${encodeURIComponent(doc.documentId)}/content`, {
                responseType: "blob",
            });
            const blobUrl = URL.createObjectURL(response.data);
            setPreviewModal({
                url: blobUrl,
                type: doc.contentType,
                title: `${doc.documentType} (${doc.originalFilename})`,
            });
        } catch {
            setError("Unable to load document preview. Please retry.");
        }
    };

    const closePreviewModal = () => {
        if (previewModal?.url) {
            URL.revokeObjectURL(previewModal.url);
        }
        setPreviewModal(null);
    };

    const formatBytes = (bytes) => {
        if (!bytes || bytes <= 0) return "0 Bytes";
        const k = 1024;
        const sizes = ["Bytes", "KB", "MB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return `${(bytes / Math.pow(k, i)).toFixed(2)} ${sizes[i]}`;
    };

    const formatDate = (instant) => {
        if (!instant) return "Recently";
        try {
            return new Date(instant).toLocaleString(undefined, {
                dateStyle: "medium",
                timeStyle: "short",
            });
        } catch {
            return instant;
        }
    };

    if (policyLoading) {
        return (
            <div className="doc-upload-container">
                <p className="doc-upload-intro" role="status">Loading document verification requirements…</p>
            </div>
        );
    }

    const availableTypes = policy?.documentTypes || [
        { type: "PAN Card", required: true },
        { type: "Masked Aadhaar", required: false },
        { type: "Voter ID", required: false },
        { type: "Passport", required: false },
    ];

    const currentTypeConfig = availableTypes.find(
        (t) => t.type.toLowerCase() === selectedType.toLowerCase()
    );

    return (
        <div className="doc-upload-container" aria-label="Document Verification Area">
            <p className="doc-upload-intro">
                Please upload clear copies of your identity documents. Files are encrypted and stored in private
                storage solely for compliance and identity verification.
            </p>

            {error && (
                <div className="doc-alert error" role="alert">
                    {error}
                </div>
            )}

            {successMessage && (
                <div className="doc-alert success" role="status">
                    ✓ {successMessage}
                </div>
            )}

            <div className="doc-upload-grid">
                {/* ================================================== */}
                {/* LEFT COLUMN: DOCUMENT TYPE & UPLOAD DROPZONE       */}
                {/* ================================================== */}
                <div className="doc-form-card">
                    {/* Document Type Dropdown */}
                    <div className="doc-field-group">
                        <label htmlFor="doc-type-selector">
                            Document Type
                            {currentTypeConfig && (
                                <span className={`doc-type-badge ${currentTypeConfig.required ? "required" : "optional"}`}>
                                    {currentTypeConfig.required ? "Required" : "Optional"}
                                </span>
                            )}
                        </label>
                        <select
                            id="doc-type-selector"
                            className="doc-select"
                            value={selectedType}
                            onChange={(e) => {
                                setSelectedType(e.target.value);
                                setError("");
                            }}
                            disabled={disabled || uploadState === UploadState.UPLOADING}
                        >
                            {availableTypes.map((dt) => (
                                <option key={dt.type} value={dt.type}>
                                    {dt.type} {dt.required ? "(Required)" : "(Optional)"}
                                </option>
                            ))}
                        </select>
                    </div>

                    {/* Masked Aadhaar Privacy & UIDAI Compliance Warning */}
                    {isAadhaarType(selectedType) && (
                        <div className="doc-privacy-callout warning" role="note">
                            <strong>UIDAI Privacy Compliance</strong>
                            Please upload a <strong>Masked Aadhaar</strong> where only the last 4 digits are visible
                            (first 8 digits masked). You may also submit a PAN Card or Voter ID as an alternative.
                        </div>
                    )}

                    {/* Aadhaar or General Consent Checkbox */}
                    <label className="doc-consent-row">
                        <input
                            type="checkbox"
                            checked={consentChecked}
                            onChange={(e) => setConsentChecked(e.target.checked)}
                            disabled={disabled || uploadState === UploadState.UPLOADING}
                        />
                        <span>
                            {isAadhaarType(selectedType)
                                ? "I confirm this document is a masked copy and consent to its use solely for identity verification."
                                : "I confirm this is my valid identity document and consent to its verification."}
                        </span>
                    </label>

                    {/* Drag and Drop Zone */}
                    <div
                        className={`doc-dropzone ${isDragActive ? "drag-active" : ""} ${selectedFile ? "has-file" : ""}`}
                        onDrop={onDrop}
                        onDragOver={onDragOver}
                        onDragLeave={onDragLeave}
                        tabIndex={0}
                        role="region"
                        aria-label="File upload dropzone"
                        onKeyDown={(e) => {
                            if ((e.key === "Enter" || e.key === " ") && !selectedFile) {
                                e.preventDefault();
                                fileInputRef.current?.click();
                            }
                        }}
                    >
                        <input
                            ref={fileInputRef}
                            type="file"
                            className="doc-file-input-hidden"
                            accept=".pdf,.jpg,.jpeg,.png"
                            onChange={(e) => {
                                if (e.target.files?.[0]) {
                                    handleFileSelection(e.target.files[0]);
                                }
                            }}
                            disabled={disabled || uploadState === UploadState.UPLOADING}
                        />

                        {!selectedFile ? (
                            <>
                                <svg
                                    className="doc-dropzone-icon"
                                    viewBox="0 0 24 24"
                                    fill="none"
                                    stroke="currentColor"
                                    strokeWidth="1.8"
                                    aria-hidden="true"
                                >
                                    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
                                    <polyline points="17 8 12 3 7 8" />
                                    <line x1="12" y1="3" x2="12" y2="15" />
                                </svg>
                                <span className="doc-dropzone-title">Drag and drop your file here</span>
                                <span className="doc-dropzone-hint">
                                    JPG, JPEG, PNG, or PDF &bull; Maximum 10 MB per file
                                </span>
                                <button
                                    type="button"
                                    className="doc-browse-btn"
                                    onClick={() => fileInputRef.current?.click()}
                                    disabled={disabled || uploadState === UploadState.UPLOADING}
                                >
                                    Browse Files
                                </button>
                            </>
                        ) : (
                            <div className="doc-selected-info">
                                <div className="doc-file-meta">
                                    <span className="doc-file-name">{selectedFile.name}</span>
                                    <span className="doc-file-size">
                                        {formatBytes(selectedFile.size)} &bull; {selectedType}
                                    </span>
                                </div>
                                <button
                                    type="button"
                                    className="doc-file-clear-btn"
                                    onClick={() => {
                                        setSelectedFile(null);
                                        cleanupLocalPreview();
                                        setUploadState(UploadState.EMPTY);
                                        if (fileInputRef.current) fileInputRef.current.value = "";
                                    }}
                                    disabled={uploadState === UploadState.UPLOADING}
                                >
                                    Change
                                </button>
                            </div>
                        )}
                    </div>

                    {/* Upload Progress Bar */}
                    {uploadState === UploadState.UPLOADING && (
                        <div className="doc-progress-container" role="progressbar" aria-valuenow={uploadProgress} aria-valuemin={0} aria-valuemax={100}>
                            <div className="doc-progress-labels">
                                <span>Uploading {selectedType}…</span>
                                <span>{uploadProgress}%</span>
                            </div>
                            <div className="doc-progress-track">
                                <div className="doc-progress-bar" style={{ width: `${uploadProgress}%` }} />
                            </div>
                        </div>
                    )}

                    {/* Upload Action Button */}
                    <button
                        type="button"
                        className="doc-action-btn"
                        onClick={handleUpload}
                        disabled={
                            disabled ||
                            !selectedFile ||
                            uploadState === UploadState.UPLOADING ||
                            (isAadhaarType(selectedType) && !consentChecked)
                        }
                    >
                        {uploadState === UploadState.UPLOADING ? (
                            <>
                                <span className="registration-spinner" aria-hidden="true" />
                                <span>Uploading…</span>
                            </>
                        ) : (
                            <span>Upload {selectedType || "Document"}</span>
                        )}
                    </button>
                </div>

                {/* ================================================== */}
                {/* RIGHT COLUMN: UPLOADED DOCUMENTS & STATUS          */}
                {/* ================================================== */}
                <div className="doc-status-card">
                    <div className="doc-status-header">
                        <h3>Uploaded Documents</h3>
                        <span className="doc-count-badge">{documents.length} Uploaded</span>
                    </div>

                    {/* Requirements checklist */}
                    <div className="doc-requirements-box">
                        <div className="doc-requirements-title">Requirement Checklist</div>
                        <ul className="doc-checklist">
                            {availableTypes.map((dt) => {
                                const isUploaded = documents.some(
                                    (d) => d.documentType.toLowerCase() === dt.type.toLowerCase()
                                );
                                return (
                                    <li
                                        key={dt.type}
                                        className={`doc-checklist-item ${isUploaded ? "satisfied" : ""}`}
                                    >
                                        <svg
                                            className="doc-check-icon"
                                            viewBox="0 0 20 20"
                                            fill="currentColor"
                                            aria-hidden="true"
                                        >
                                            {isUploaded ? (
                                                <path
                                                    fillRule="evenodd"
                                                    d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                                                    clipRule="evenodd"
                                                />
                                            ) : (
                                                <circle cx="10" cy="10" r="7" stroke="currentColor" strokeWidth="2" fill="none" />
                                            )}
                                        </svg>
                                        <span>
                                            {dt.type} {dt.required ? "(Mandatory)" : "(Optional)"}
                                        </span>
                                    </li>
                                );
                            })}
                        </ul>
                    </div>

                    {/* Documents List */}
                    {documents.length === 0 ? (
                        <div className="doc-empty-state">
                            No documents uploaded yet. Please upload the required documents on the left.
                        </div>
                    ) : (
                        <div className="doc-list">
                            {documents.map((doc) => {
                                const status = String(doc.verificationStatus || "PENDING").toLowerCase();
                                return (
                                    <div key={doc.documentId} className="doc-item-card">
                                        <div className="doc-item-header">
                                            <span className="doc-item-type">{doc.documentType}</span>
                                            <span className={`doc-badge ${status}`}>
                                                {status === "approved" || status === "verified" ? (
                                                    "✓ Approved"
                                                ) : status === "rejected" ? (
                                                    "✕ Rejected"
                                                ) : status === "re_upload" ? (
                                                    "↺ Re-upload Needed"
                                                ) : (
                                                    "⏳ Under Review"
                                                )}
                                            </span>
                                        </div>

                                        <div className="doc-item-details">
                                            <span>File: {doc.originalFilename}</span>
                                            <span>
                                                Size: {formatBytes(doc.fileSize)} &bull; Uploaded: {formatDate(doc.uploadedAt)}
                                            </span>
                                        </div>

                                        <div className="doc-item-actions">
                                            <button
                                                type="button"
                                                className="doc-item-btn"
                                                onClick={() => handleViewDocument(doc)}
                                                title="Preview document"
                                            >
                                                👁 Preview
                                            </button>
                                            <button
                                                type="button"
                                                className="doc-item-btn replace"
                                                onClick={() => handleStartReplace(doc)}
                                                disabled={disabled || uploadState === UploadState.UPLOADING}
                                                title="Replace this document"
                                            >
                                                ↺ Replace
                                            </button>
                                            <button
                                                type="button"
                                                className="doc-item-btn remove"
                                                onClick={() => handleRemoveDocument(doc)}
                                                disabled={disabled || uploadState === UploadState.UPLOADING}
                                                title="Remove this document"
                                            >
                                                🗑 Remove
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            </div>

            {/* ================================================== */}
            {/* DOCUMENT PREVIEW MODAL                             */}
            {/* ================================================== */}
            {previewModal && (
                <div className="doc-modal-overlay" onClick={closePreviewModal} role="dialog" aria-modal="true">
                    <div className="doc-modal-content" onClick={(e) => e.stopPropagation()}>
                        <div className="doc-modal-header">
                            <h4>{previewModal.title}</h4>
                            <button
                                type="button"
                                className="doc-modal-close"
                                onClick={closePreviewModal}
                                aria-label="Close preview"
                            >
                                &times;
                            </button>
                        </div>
                        <div className="doc-modal-body">
                            {previewModal.type?.includes("pdf") ? (
                                <embed
                                    src={previewModal.url}
                                    type="application/pdf"
                                    className="doc-preview-pdf-embed"
                                    title="PDF Document Preview"
                                />
                            ) : (
                                <img
                                    src={previewModal.url}
                                    alt={previewModal.title}
                                    className="doc-preview-img"
                                />
                            )}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
