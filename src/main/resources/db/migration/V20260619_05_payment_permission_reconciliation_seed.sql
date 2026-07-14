-- Ensure Admin and Super Admin have payment permissions needed by secured payment endpoints.
-- Safe for reruns because role_payment_permissions has a unique key and INSERT IGNORE avoids duplicates.

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
('SUPER_ADMIN', 'PAYMENT_RECONCILE'),
('ADMIN', 'PAYMENT_VIEW'),
('ADMIN', 'PAYMENT_RECONCILE');
