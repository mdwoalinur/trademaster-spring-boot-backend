-- Payment workflow cleanup: preserve financial history, repair safe historical rows,
-- and make payment reference_id nullable because multi-reference payments are stored
-- in payment_allocations.

ALTER TABLE payments MODIFY COLUMN reference_id BIGINT NULL;

UPDATE payments
SET voucher_no = CONCAT(
        CASE
            WHEN direction = 'RECEIVE' THEN 'RCV'
            WHEN direction = 'PAY' THEN 'PAY'
            WHEN direction = 'REFUND' THEN 'REF'
            WHEN direction = 'TRANSFER' THEN 'TRF'
            ELSE 'PV'
        END,
        '-2026-',
        LPAD(payment_id, 6, '0')
    )
WHERE (voucher_no IS NULL OR voucher_no = '');

UPDATE payments
SET requested_amount = COALESCE(NULLIF(requested_amount, 0), amount, 0)
WHERE requested_amount IS NULL OR requested_amount = 0;

UPDATE payments
SET approved_amount = COALESCE(NULLIF(approved_amount, 0), requested_amount, amount, 0)
WHERE approval_status = 'APPROVED'
  AND transaction_status = 'POSTED'
  AND (approved_amount IS NULL OR approved_amount = 0);

INSERT INTO payment_allocations (
    payment_id,
    reference_type,
    reference_id,
    allocated_amount,
    discount_amount,
    write_off_amount,
    created_at
)
SELECT
    p.payment_id,
    CASE
        WHEN p.payment_type = 'EXPENSE' THEN 'EXPENSE'
        WHEN p.payment_type IN ('PURCHASE', 'SUPPLIER_ADVANCE') THEN 'PURCHASE'
        WHEN p.payment_type = 'REFUND' THEN 'REFUND'
        ELSE 'SALE'
    END,
    p.reference_id,
    COALESCE(NULLIF(p.approved_amount, 0), NULLIF(p.requested_amount, 0), p.amount, 0),
    0,
    0,
    COALESCE(p.created_at, CURRENT_TIMESTAMP)
FROM payments p
LEFT JOIN payment_allocations pa ON pa.payment_id = p.payment_id
WHERE pa.allocation_id IS NULL
  AND p.reference_id IS NOT NULL
  AND p.reference_id > 0
  AND COALESCE(NULLIF(p.approved_amount, 0), NULLIF(p.requested_amount, 0), p.amount, 0) > 0
  AND p.payment_type NOT IN ('ACCOUNT_TRANSFER');

UPDATE payments
SET transaction_status = 'POSTED',
    payment_status = 'PAID',
    approved_amount = COALESCE(NULLIF(approved_amount, 0), NULLIF(requested_amount, 0), amount, 0),
    posted_at = COALESCE(posted_at, approved_at, updated_at, created_at, CURRENT_TIMESTAMP)
WHERE approval_status = 'APPROVED'
  AND transaction_status = 'CANCELLED'
  AND reference_id IS NOT NULL
  AND reference_id > 0
  AND COALESCE(NULLIF(approved_amount, 0), NULLIF(requested_amount, 0), amount, 0) > 0
  AND EXISTS (
      SELECT 1
      FROM payment_allocations pa
      WHERE pa.payment_id = payments.payment_id
  );

UPDATE payments
SET approval_status = 'REJECTED',
    payment_status = 'UNPAID',
    rejection_reason = COALESCE(rejection_reason, 'Historical APPROVED+CANCELLED state was unsafe and marked for manual review'),
    notes = CONCAT(COALESCE(notes, ''), CASE WHEN notes IS NULL OR notes = '' THEN '' ELSE '\n' END,
                   'Manual review required: historical APPROVED+CANCELLED state had missing/ambiguous financial reference.')
WHERE approval_status = 'APPROVED'
  AND transaction_status = 'CANCELLED';

CREATE OR REPLACE VIEW payment_migration_manual_review AS
SELECT payment_id,
       voucher_no,
       payment_type,
       party_type,
       party_id,
       reference_id,
       requested_amount,
       approved_amount,
       amount,
       approval_status,
       transaction_status,
       notes
FROM payments
WHERE notes LIKE '%Manual review required: historical APPROVED+CANCELLED%';
