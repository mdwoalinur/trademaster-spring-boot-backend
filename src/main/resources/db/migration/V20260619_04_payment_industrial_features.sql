-- TradeMaster industrial payment system extensions.
-- Safe additive migration for MySQL 8. Keeps existing payment data and does not post any financial impact.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS original_payment_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS reversal_payment_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS refund_reason TEXT NULL,
    ADD COLUMN IF NOT EXISTS cash_drawer VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS received_amount DECIMAL(19,4) NULL,
    ADD COLUMN IF NOT EXISTS change_amount DECIMAL(19,4) NULL,
    ADD COLUMN IF NOT EXISTS transfer_date DATETIME NULL,
    ADD COLUMN IF NOT EXISTS sender_receiver_reference VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS cheque_date DATETIME NULL,
    ADD COLUMN IF NOT EXISTS expected_clearing_date DATETIME NULL,
    ADD COLUMN IF NOT EXISTS cheque_status VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS approval_code VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS terminal_reference VARCHAR(150) NULL;

CREATE INDEX IF NOT EXISTS idx_payments_original_payment_id ON payments (original_payment_id);
CREATE INDEX IF NOT EXISTS idx_payments_reversal_payment_id ON payments (reversal_payment_id);
CREATE INDEX IF NOT EXISTS idx_payments_reconciliation_status ON payments (reconciliation_status);

CREATE TABLE IF NOT EXISTS payment_attachments (
    attachment_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    content_type VARCHAR(120) NULL,
    file_size BIGINT NULL,
    uploaded_by BIGINT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (attachment_id),
    KEY idx_payment_attachments_payment_id (payment_id),
    CONSTRAINT fk_payment_attachments_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
);

CREATE TABLE IF NOT EXISTS bank_statement_entries (
    statement_entry_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    transaction_date DATETIME NOT NULL,
    value_date DATETIME NULL,
    description TEXT NULL,
    statement_reference VARCHAR(150) NULL,
    debit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    external_balance DECIMAL(19,4) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNMATCHED',
    matched_payment_id BIGINT NULL,
    import_batch_id VARCHAR(100) NULL,
    imported_by BIGINT NULL,
    imported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reconciled_by BIGINT NULL,
    reconciled_at DATETIME NULL,
    comments TEXT NULL,
    PRIMARY KEY (statement_entry_id),
    KEY idx_bank_statement_account_date (account_id, transaction_date),
    KEY idx_bank_statement_status (status),
    KEY idx_bank_statement_reference (statement_reference),
    KEY idx_bank_statement_matched_payment (matched_payment_id),
    CONSTRAINT fk_bank_statement_account
        FOREIGN KEY (account_id) REFERENCES financial_accounts (account_id)
);

CREATE TABLE IF NOT EXISTS role_payment_permissions (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(80) NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (permission_id),
    UNIQUE KEY ux_role_payment_permission (role_name, permission_code)
);

INSERT IGNORE INTO role_payment_permissions (role_name, permission_code) VALUES
('SUPER_ADMIN', 'PAYMENT_VIEW'),
('SUPER_ADMIN', 'PAYMENT_CREATE'),
('SUPER_ADMIN', 'PAYMENT_EDIT_DRAFT'),
('SUPER_ADMIN', 'PAYMENT_SUBMIT'),
('SUPER_ADMIN', 'PAYMENT_APPROVE'),
('SUPER_ADMIN', 'PAYMENT_REJECT'),
('SUPER_ADMIN', 'PAYMENT_RETURN'),
('SUPER_ADMIN', 'PAYMENT_POST'),
('SUPER_ADMIN', 'PAYMENT_CANCEL'),
('SUPER_ADMIN', 'PAYMENT_VOID'),
('SUPER_ADMIN', 'PAYMENT_REFUND'),
('SUPER_ADMIN', 'PAYMENT_RECONCILE'),
('SUPER_ADMIN', 'PAYMENT_ACCOUNT_VIEW'),
('SUPER_ADMIN', 'PAYMENT_ACCOUNT_MANAGE'),
('SUPER_ADMIN', 'PAYMENT_LEDGER_VIEW'),
('SUPER_ADMIN', 'PAYMENT_ATTACHMENT_MANAGE'),
('SUPER_ADMIN', 'PAYMENT_REPORT_VIEW'),
('SUPER_ADMIN', 'PAYMENT_DASHBOARD_VIEW'),
('SUPER_ADMIN', 'PAYMENT_SELF_APPROVE'),
('ADMIN', 'PAYMENT_VIEW'),
('ADMIN', 'PAYMENT_CREATE'),
('ADMIN', 'PAYMENT_EDIT_DRAFT'),
('ADMIN', 'PAYMENT_SUBMIT'),
('ADMIN', 'PAYMENT_APPROVE'),
('ADMIN', 'PAYMENT_REJECT'),
('ADMIN', 'PAYMENT_RETURN'),
('ADMIN', 'PAYMENT_POST'),
('ADMIN', 'PAYMENT_CANCEL'),
('ADMIN', 'PAYMENT_VOID'),
('ADMIN', 'PAYMENT_REFUND'),
('ADMIN', 'PAYMENT_RECONCILE'),
('ADMIN', 'PAYMENT_ACCOUNT_VIEW'),
('ADMIN', 'PAYMENT_ACCOUNT_MANAGE'),
('ADMIN', 'PAYMENT_LEDGER_VIEW'),
('ADMIN', 'PAYMENT_ATTACHMENT_MANAGE'),
('ADMIN', 'PAYMENT_REPORT_VIEW'),
('ADMIN', 'PAYMENT_DASHBOARD_VIEW'),
('MANAGER', 'PAYMENT_VIEW'),
('MANAGER', 'PAYMENT_CREATE'),
('MANAGER', 'PAYMENT_SUBMIT'),
('EMPLOYEE', 'PAYMENT_VIEW'),
('EMPLOYEE', 'PAYMENT_CREATE'),
('EMPLOYEE', 'PAYMENT_EDIT_DRAFT'),
('EMPLOYEE', 'PAYMENT_SUBMIT'),
('EMPLOYEE', 'PAYMENT_ATTACHMENT_MANAGE'),
('SALESMAN', 'PAYMENT_VIEW'),
('SALESMAN', 'PAYMENT_CREATE'),
('SALESMAN', 'PAYMENT_EDIT_DRAFT'),
('SALESMAN', 'PAYMENT_SUBMIT'),
('SALESMAN', 'PAYMENT_ATTACHMENT_MANAGE');
