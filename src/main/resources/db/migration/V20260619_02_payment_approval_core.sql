-- TradeMaster centralized payment approval core.
-- Safe manual migration for MySQL 8. Run against the trademaster database before production use.
-- This script preserves existing payment IDs and data.

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS voucher_no VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS direction VARCHAR(30) NOT NULL DEFAULT 'RECEIVE',
    ADD COLUMN IF NOT EXISTS party_type VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS party_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS requested_amount DECIMAL(19,4) NULL,
    ADD COLUMN IF NOT EXISTS approved_amount DECIMAL(19,4) NULL,
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(10) NOT NULL DEFAULT 'BDT',
    ADD COLUMN IF NOT EXISTS exchange_rate DECIMAL(19,6) NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS account_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS transaction_reference VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    ADD COLUMN IF NOT EXISTS transaction_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS reconciliation_status VARCHAR(30) NOT NULL DEFAULT 'UNRECONCILED',
    ADD COLUMN IF NOT EXISTS company_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS warehouse_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS created_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS submitted_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS submitted_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS approved_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS approved_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS rejected_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS rejected_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT NULL,
    ADD COLUMN IF NOT EXISTS posted_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS posted_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS voided_by BIGINT NULL,
    ADD COLUMN IF NOT EXISTS voided_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS void_reason TEXT NULL,
    ADD COLUMN IF NOT EXISTS bank_name VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS cheque_number VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS mobile_provider VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS mobile_transaction_id VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS card_type VARCHAR(50) NULL,
    ADD COLUMN IF NOT EXISTS card_last_four VARCHAR(4) NULL,
    ADD COLUMN IF NOT EXISTS gateway_reference VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS version BIGINT NULL;

UPDATE payments
SET voucher_no = CONCAT('PV-HIST-', payment_id)
WHERE voucher_no IS NULL OR voucher_no = '';

UPDATE payments
SET requested_amount = amount
WHERE requested_amount IS NULL;

UPDATE payments
SET approved_amount = amount,
    approval_status = 'APPROVED',
    transaction_status = 'POSTED',
    posted_at = COALESCE(payment_date, created_at),
    approved_at = COALESCE(payment_date, created_at),
    created_by = COALESCE(created_by, received_by),
    posted_by = COALESCE(posted_by, received_by),
    approved_by = COALESCE(approved_by, received_by)
WHERE payment_status = 'PAID'
  AND approval_status IN ('DRAFT', 'PENDING_APPROVAL');

CREATE UNIQUE INDEX IF NOT EXISTS ux_payments_voucher_no ON payments (voucher_no);
CREATE INDEX IF NOT EXISTS idx_payments_approval_status ON payments (approval_status);
CREATE INDEX IF NOT EXISTS idx_payments_transaction_status ON payments (transaction_status);
CREATE INDEX IF NOT EXISTS idx_payments_payment_date ON payments (payment_date);
CREATE INDEX IF NOT EXISTS idx_payments_party ON payments (party_type, party_id);
CREATE INDEX IF NOT EXISTS idx_payments_company_id ON payments (company_id);
CREATE INDEX IF NOT EXISTS idx_payments_account_id ON payments (account_id);

CREATE TABLE IF NOT EXISTS payment_allocations (
    allocation_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    reference_id BIGINT NOT NULL,
    allocated_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(19,4) NULL DEFAULT 0,
    write_off_amount DECIMAL(19,4) NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (allocation_id),
    INDEX idx_payment_allocations_payment_id (payment_id),
    INDEX idx_payment_allocations_reference (reference_type, reference_id),
    CONSTRAINT fk_payment_allocations_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
);

INSERT INTO payment_allocations (payment_id, reference_type, reference_id, allocated_amount, created_at)
SELECT p.payment_id,
       CASE
           WHEN p.payment_type = 'EXPENSE' THEN 'EXPENSE'
           WHEN p.payment_type = 'PURCHASE' THEN 'PURCHASE'
           ELSE 'SALE'
       END,
       p.reference_id,
       COALESCE(p.approved_amount, p.requested_amount, p.amount, 0),
       COALESCE(p.created_at, NOW())
FROM payments p
LEFT JOIN payment_allocations a ON a.payment_id = p.payment_id
WHERE a.allocation_id IS NULL
  AND p.reference_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS payment_approval_history (
    approval_history_id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    previous_status VARCHAR(50) NULL,
    new_status VARCHAR(50) NULL,
    comments TEXT NULL,
    acted_by BIGINT NOT NULL,
    acted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (approval_history_id),
    INDEX idx_payment_approval_history_payment_id (payment_id),
    CONSTRAINT fk_payment_approval_history_payment
        FOREIGN KEY (payment_id) REFERENCES payments (payment_id)
);
