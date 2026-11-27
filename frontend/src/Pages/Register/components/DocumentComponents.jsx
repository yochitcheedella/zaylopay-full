import React, { useEffect, useRef, useState } from 'react';
import './DocumentComponents.css';

export function UploadProgress({ progress = 0, filename }) {
  return (
    <div className="zp-upload-progress" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
      <div className="zp-progress-meta">
        <span className="zp-progress-name">{filename || 'Uploading document…'}</span>
        <span className="zp-progress-pct">{progress}%</span>
      </div>
      <div className="zp-progress-track">
        <div className="zp-progress-bar" style={{ width: `${Math.min(100, Math.max(0, progress))}%` }} />
      </div>
    </div>
  );
}

export function FilePreviewModal({ url, contentType, filename, onClose }) {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const isPdf = contentType === 'application/pdf' || (filename && filename.toLowerCase().endsWith('.pdf'));

  return (
    <div className="zp-modal-backdrop" onClick={onClose} role="dialog" aria-modal="true" aria-label={`Preview of ${filename}`}>
      <div className="zp-modal-dialog" onClick={(e) => e.stopPropagation()}>
        <div className="zp-modal-header">
          <div className="zp-modal-title-wrapper">
            <h3 className="zp-modal-title">{filename || 'Document preview'}</h3>
            <span className="zp-modal-type-badge">{isPdf ? 'PDF Document' : 'Image'}</span>
          </div>
          <button type="button" className="zp-modal-close" onClick={onClose} aria-label="Close preview">
            <svg viewBox="0 0 20 20" fill="currentColor">
              <path fillRule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clipRule="evenodd"/>
            </svg>
          </button>
        </div>

        <div className="zp-modal-body">
          {isPdf ? (
            <iframe src={url} title={filename} className="zp-modal-iframe" />
          ) : (
            <div className="zp-modal-img-container">
              <img src={url} alt={filename} className="zp-modal-img" />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function DocumentItem({
  document,
  onPreview,
  onReplace,
  onRemove,
  disabled = false,
}) {
  const isPdf = document.contentType === 'application/pdf' || document.originalFilename?.toLowerCase().endsWith('.pdf');
  const sizeMb = (document.fileSize / (1024 * 1024)).toFixed(2);
  const sizeKb = Math.round(document.fileSize / 1024);
  const displaySize = document.fileSize >= 1024 * 1024 ? `${sizeMb} MB` : `${sizeKb} KB`;

  const statusLabel = document.verificationStatus === 'APPROVED'
    ? 'Approved'
    : document.verificationStatus === 'REJECTED'
    ? 'Rejected'
    : 'Pending review';

  const statusClass = document.verificationStatus === 'APPROVED'
    ? 'is-approved'
    : document.verificationStatus === 'REJECTED'
    ? 'is-rejected'
    : 'is-pending';

  return (
    <div className={`zp-doc-item ${statusClass}`}>
      <div className="zp-doc-icon-wrap" aria-hidden="true">
        {isPdf ? (
          <svg className="zp-doc-file-icon is-pdf" viewBox="0 0 24 24" fill="currentColor">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" opacity=".2"/>
            <path fillRule="evenodd" d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8l-6-6zm4 18H6V4h7v5h5v11z"/>
          </svg>
        ) : (
          <svg className="zp-doc-file-icon is-img" viewBox="0 0 24 24" fill="currentColor">
            <path d="M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zm-1 16H6l4-5 3 3.5 2-2.5 3 4z" opacity=".2"/>
            <path fillRule="evenodd" d="M19 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V5a2 2 0 0 0-2-2zM5 19V5h14v14H5zm10-7l-2 2.5-3-3.5-4 5h12l-3-4z"/>
          </svg>
        )}
      </div>

      <div className="zp-doc-info">
        <div className="zp-doc-main-row">
          <strong className="zp-doc-type">{document.documentType}</strong>
          <span className={`zp-doc-status-badge ${statusClass}`}>{statusLabel}</span>
        </div>
        <p className="zp-doc-filename" title={document.originalFilename}>
          {document.originalFilename}
        </p>
        <span className="zp-doc-meta">{displaySize}</span>
      </div>

      <div className="zp-doc-actions">
        {onPreview && (
          <button
            type="button"
            className="zp-doc-btn zp-doc-preview-btn"
            onClick={() => onPreview(document)}
            title="Preview document"
            aria-label={`Preview ${document.originalFilename}`}
            disabled={disabled}
          >
            Preview
          </button>
        )}
        {onReplace && (
          <button
            type="button"
            className="zp-doc-btn zp-doc-replace-btn"
            onClick={() => onReplace(document)}
            title="Replace document"
            aria-label={`Replace ${document.originalFilename}`}
            disabled={disabled}
          >
            Replace
          </button>
        )}
        {onRemove && (
          <button
            type="button"
            className="zp-doc-btn zp-doc-remove-btn"
            onClick={() => onRemove(document)}
            title="Remove document"
            aria-label={`Remove ${document.originalFilename}`}
            disabled={disabled}
          >
            Remove
          </button>
        )}
      </div>
    </div>
  );
}

export function DocumentUploader({
  documentTypes = [
    { type: 'PAN Card', label: 'PAN Card', required: true },
    { type: 'Masked Aadhaar', label: 'Masked Aadhaar (Recommended)', required: false },
    { type: 'Voter ID', label: 'Voter ID', required: false },
  ],
  selectedType,
  onTypeChange,
  onFileSelect,
  selectedFile,
  consentChecked,
  onConsentChange,
  uploading = false,
  uploadProgress = 0,
  onUploadSubmit,
  disabled = false,
  error,
}) {
  const [isDragOver, setIsDragOver] = useState(false);
  const fileInputRef = useRef(null);

  const isAadhaar = /aadhaar/i.test(selectedType || '');

  const handleDragOver = (e) => {
    e.preventDefault();
    if (!disabled && !uploading) setIsDragOver(true);
  };

  const handleDragLeave = (e) => {
    e.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setIsDragOver(false);
    if (disabled || uploading) return;
    const files = e.dataTransfer.files;
    if (files && files.length > 0) {
      onFileSelect(files[0]);
    }
  };

  const handleFileChange = (e) => {
    const files = e.target.files;
    if (files && files.length > 0) {
      onFileSelect(files[0]);
    }
  };

  return (
    <div className="zp-uploader-container">
      {/* Document Type Selection */}
      <div className="zp-doc-type-selector">
        <label htmlFor="zp-document-type" className="zp-field-label">
          Select document type to upload
          <span className="zp-required-star" aria-hidden="true">*</span>
        </label>
        <select
          id="zp-document-type"
          value={selectedType}
          onChange={(e) => onTypeChange(e.target.value)}
          disabled={disabled || uploading}
          className="zp-field-select zp-doc-select"
        >
          {documentTypes.map((dt) => (
            <option key={dt.type} value={dt.type}>
              {dt.label || dt.type} {dt.required ? '(Required)' : '(Optional)'}
            </option>
          ))}
        </select>
      </div>

      {/* Aadhaar Privacy Guidance */}
      {isAadhaar && (
        <div className="zp-aadhaar-consent-box">
          <div className="zp-aadhaar-header">
            <svg className="zp-shield-icon" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fillRule="evenodd" d="M10 1.944A11.954 11.954 0 012.166 5C2.056 5.649 2 6.319 2 7c0 5.225 3.34 9.67 8 11.317C14.66 16.67 18 12.225 18 7c0-.682-.057-1.35-.166-2.001A11.954 11.954 0 0110 1.944z" clipRule="evenodd" />
            </svg>
            <strong>Masked Aadhaar Guidelines (UIDAI Compliant)</strong>
          </div>
          <p className="zp-aadhaar-desc">
            Please ensure the first 8 digits of your Aadhaar number are masked (showing only the last 4 digits). We do not collect or store full Aadhaar numbers.
          </p>
          <label className="zp-checkbox-label">
            <input
              type="checkbox"
              checked={consentChecked}
              onChange={(e) => onConsentChange(e.target.checked)}
              disabled={disabled || uploading}
              className="zp-checkbox-input"
            />
            <span>I confirm that the uploaded Aadhaar is masked and consent to its voluntary use for verification.</span>
          </label>
        </div>
      )}

      {/* Drag & Drop Area */}
      <div
        className={`zp-dropzone ${isDragOver ? 'is-dragover' : ''} ${selectedFile ? 'has-file' : ''} ${disabled ? 'is-disabled' : ''}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => !disabled && !uploading && fileInputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(e) => {
          if ((e.key === 'Enter' || e.key === ' ') && !disabled && !uploading) {
            e.preventDefault();
            fileInputRef.current?.click();
          }
        }}
        aria-label="Upload document file drop zone"
      >
        <input
          ref={fileInputRef}
          type="file"
          accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf"
          onChange={handleFileChange}
          style={{ display: 'none' }}
          disabled={disabled || uploading}
        />

        <div className="zp-dropzone-content">
          <div className="zp-dropzone-icon-circle" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path strokeLinecap="round" strokeLinejoin="round" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
          </div>

          {selectedFile ? (
            <div className="zp-selected-file-info">
              <span className="zp-selected-file-name">{selectedFile.name}</span>
              <span className="zp-selected-file-size">
                {(selectedFile.size / (1024 * 1024)).toFixed(2)} MB • Ready to upload
              </span>
              <span className="zp-change-file-hint">Click or drop another file to replace</span>
            </div>
          ) : (
            <div className="zp-dropzone-text">
              <span className="zp-dropzone-prompt">
                <span className="zp-browse-link">Browse files</span> or drag & drop here
              </span>
              <span className="zp-dropzone-limits">
                Accepted: JPG, JPEG, PNG, PDF • Maximum 10 MB per file
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Uploading progress bar */}
      {uploading && (
        <UploadProgress progress={uploadProgress} filename={selectedFile?.name} />
      )}

      {/* Upload Button */}
      {selectedFile && !uploading && (
        <div className="zp-upload-action-row">
          <button
            type="button"
            className="zp-primary-button zp-upload-now-btn"
            onClick={onUploadSubmit}
            disabled={disabled || (isAadhaar && !consentChecked)}
          >
            Upload {selectedType}
          </button>
        </div>
      )}

      {error && (
        <p className="zp-field-error zp-uploader-error" role="alert">
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}
