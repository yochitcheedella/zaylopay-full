-- V10: Add payment orders, transactions, webhooks, admin management, approval history, daily counters, and affiliate memberships

ALTER TABLE applicants
    ADD COLUMN payment_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER registration_submitted_at,
    ADD COLUMN approval_status VARCHAR(24) NOT NULL DEFAULT 'PENDING' AFTER payment_status,
    ADD COLUMN approved_at TIMESTAMP(6) NULL AFTER approval_status,
    ADD COLUMN rejected_at TIMESTAMP(6) NULL AFTER approved_at,
    ADD COLUMN rejection_reason VARCHAR(500) NULL AFTER rejected_at,
    ADD COLUMN affiliate_id VARCHAR(32) NULL AFTER rejection_reason;

ALTER TABLE applicants
    ADD CONSTRAINT uk_applicants_affiliate_id UNIQUE (affiliate_id);

CREATE TABLE applicant_bank_details (
    bank_detail_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    applicant_id BIGINT UNSIGNED NOT NULL,
    account_holder_name VARCHAR(150) NOT NULL,
    bank_name VARCHAR(120) NOT NULL,
    account_number_ciphertext VARCHAR(512) NOT NULL,
    account_number_last4 VARCHAR(4) NOT NULL,
    masked_account_number VARCHAR(32) NOT NULL,
    ifsc_code VARCHAR(16) NOT NULL,
    consent_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (bank_detail_id),
    CONSTRAINT uk_applicant_bank_details_applicant UNIQUE (applicant_id),
    CONSTRAINT fk_applicant_bank_details_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_orders (
    order_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    applicant_id BIGINT UNSIGNED NOT NULL,
    gateway_order_id VARCHAR(64) NOT NULL,
    amount_paise BIGINT UNSIGNED NOT NULL DEFAULT 59900,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    receipt VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (order_id),
    CONSTRAINT uk_payment_orders_gateway_order UNIQUE (gateway_order_id),
    CONSTRAINT fk_payment_orders_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id) ON DELETE RESTRICT,
    INDEX ix_payment_orders_applicant (applicant_id),
    INDEX ix_payment_orders_status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_transactions (
    transaction_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    payment_order_id BIGINT UNSIGNED NOT NULL,
    applicant_id BIGINT UNSIGNED NOT NULL,
    gateway_payment_id VARCHAR(64) NOT NULL,
    gateway_order_id VARCHAR(64) NOT NULL,
    amount_paise BIGINT UNSIGNED NOT NULL DEFAULT 59900,
    currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    method VARCHAR(32) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CAPTURED',
    signature VARCHAR(128) NULL,
    signature_verified BOOLEAN NOT NULL DEFAULT FALSE,
    captured_at TIMESTAMP(6) NULL,
    error_code VARCHAR(64) NULL,
    error_description VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (transaction_id),
    CONSTRAINT uk_payment_tx_gateway_payment UNIQUE (gateway_payment_id),
    CONSTRAINT fk_payment_tx_order FOREIGN KEY (payment_order_id) REFERENCES payment_orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_tx_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id) ON DELETE RESTRICT,
    INDEX ix_payment_tx_order (payment_order_id),
    INDEX ix_payment_tx_applicant (applicant_id),
    INDEX ix_payment_tx_status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_webhook_events (
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP(6) NULL,
    processing_error VARCHAR(255) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (event_id),
    INDEX ix_payment_webhook_processed (processed)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_users (
    admin_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'ADMIN',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (admin_id),
    CONSTRAINT uk_admin_users_username UNIQUE (username),
    CONSTRAINT uk_admin_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_sessions (
    session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    admin_id BIGINT UNSIGNED NOT NULL,
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT uk_admin_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_admin_sessions_admin FOREIGN KEY (admin_id) REFERENCES admin_users (admin_id) ON DELETE CASCADE,
    INDEX ix_admin_sessions_admin (admin_id),
    INDEX ix_admin_sessions_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_approval_history (
    approval_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    applicant_id BIGINT UNSIGNED NOT NULL,
    admin_username VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason_or_notes VARCHAR(500) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (approval_id),
    CONSTRAINT fk_approval_history_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id) ON DELETE RESTRICT,
    INDEX ix_approval_history_applicant (applicant_id),
    INDEX ix_approval_history_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_affiliate_id_counters (
    counter_date DATE NOT NULL,
    last_serial INT UNSIGNED NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (counter_date)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE affiliate_memberships (
    membership_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    applicant_id BIGINT UNSIGNED NOT NULL,
    affiliate_id VARCHAR(32) NOT NULL,
    referral_code VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    tier VARCHAR(24) NOT NULL DEFAULT 'AFFILIATE',
    approval_id BIGINT UNSIGNED NULL,
    activated_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (membership_id),
    CONSTRAINT uk_affiliate_memberships_applicant UNIQUE (applicant_id),
    CONSTRAINT uk_affiliate_memberships_affiliate_id UNIQUE (affiliate_id),
    CONSTRAINT uk_affiliate_memberships_referral_code UNIQUE (referral_code),
    CONSTRAINT fk_affiliate_memberships_applicant FOREIGN KEY (applicant_id) REFERENCES applicants (applicant_id) ON DELETE RESTRICT,
    INDEX ix_affiliate_memberships_status (status)
) ENGINE=InnoDB DEFAULT CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
