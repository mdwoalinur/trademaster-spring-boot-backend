-- TradeMaster financial accounts, immutable ledgers, and purchase settlement.
-- Safe additive migration for MySQL 8. Does not drop existing data.

CREATE TABLE IF NOT EXISTS financial_accounts (
    account_id BIGINT NOT NULL AUTO_INCREMENT,
    account_code VARCHAR(100) NOT NULL,
    account_name VARCHAR(200) NOT NULL,
    account_type VARCHAR(40) NOT NULL DEFAULT 'CASH',
    account_number VARCHAR(100) NULL,
    bank_name VARCHAR(150) NULL,
    branch_name VARCHAR(150) NULL,
    mobile_provider VARCHAR(100) NULL,
    currency_code VARCHAR(10) NULL DEFAULT 'BDT',
    opening_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    current_balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    allow_overdraft BIT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    company_id BIGINT NULL,
    warehouse_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL,
    version BIGINT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY ux_financial_accounts_code (account_code),
    KEY idx_financial_accounts_type (account_type),
    KEY idx_financial_accounts_status (status)
);

CREATE TABLE IF NOT EXISTS account_ledger_entries (
    ledger_entry_id BIGINT NOT NULL AUTO_INCREMENT,
    account_id BIGINT NOT NULL,
    payment_id BIGINT NULL,
    entry_type VARCHAR(50) NOT NULL,
    direction VARCHAR(30) NOT NULL,
    debit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    credit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    balance_before DECIMAL(19,4) NOT NULL DEFAULT 0,
    balance_after DECIMAL(19,4) NOT NULL DEFAULT 0,
    reference_type VARCHAR(40) NULL,
    reference_id BIGINT NULL,
    voucher_no VARCHAR(100) NULL,
    description TEXT NULL,
    posted_by BIGINT NULL,
    posted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    company_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversal_of_entry_id BIGINT NULL,
    PRIMARY KEY (ledger_entry_id),
    KEY idx_account_ledger_account_date (account_id, posted_at),
    KEY idx_account_ledger_payment (payment_id),
    KEY idx_account_ledger_reference (reference_type, reference_id),
    CONSTRAINT fk_account_ledger_account FOREIGN KEY (account_id) REFERENCES financial_accounts (account_id)
);

CREATE TABLE IF NOT EXISTS party_ledger_entries (
    party_ledger_entry_id BIGINT NOT NULL AUTO_INCREMENT,
    party_type VARCHAR(30) NOT NULL,
    party_id BIGINT NOT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id BIGINT NULL,
    payment_id BIGINT NULL,
    document_amount DECIMAL(19,4) NULL DEFAULT 0,
    debit_amount DECIMAL(19,4) NULL DEFAULT 0,
    credit_amount DECIMAL(19,4) NULL DEFAULT 0,
    balance_after DECIMAL(19,4) NULL DEFAULT 0,
    entry_type VARCHAR(50) NOT NULL,
    description TEXT NULL,
    posted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    company_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversal_of_entry_id BIGINT NULL,
    PRIMARY KEY (party_ledger_entry_id),
    KEY idx_party_ledger_party_date (party_type, party_id, posted_at),
    KEY idx_party_ledger_payment (payment_id),
    KEY idx_party_ledger_reference (reference_type, reference_id)
);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS destination_account_id BIGINT NULL;

ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS opening_balance DECIMAL(15,2) NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS current_balance DECIMAL(15,2) NULL DEFAULT 0;

ALTER TABLE purchases
    ADD COLUMN IF NOT EXISTS paid_amount DECIMAL(15,2) NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS due_amount DECIMAL(15,2) NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID';

UPDATE purchases
SET due_amount = COALESCE(total_amount, 0) - COALESCE(paid_amount, 0),
    payment_status = CASE
        WHEN COALESCE(paid_amount, 0) <= 0 THEN 'UNPAID'
        WHEN COALESCE(paid_amount, 0) >= COALESCE(total_amount, 0) THEN 'PAID'
        ELSE 'PARTIAL'
    END;

-- Optional starter account. Use only for testing or map real historical payments manually before production posting.
INSERT INTO financial_accounts (account_code, account_name, account_type, opening_balance, current_balance, status, company_id)
SELECT 'UNASSIGNED-HISTORICAL', 'Unassigned Historical Account', 'OTHER', 0, 0, 'ACTIVE', 1
WHERE NOT EXISTS (SELECT 1 FROM financial_accounts WHERE account_code = 'UNASSIGNED-HISTORICAL');
