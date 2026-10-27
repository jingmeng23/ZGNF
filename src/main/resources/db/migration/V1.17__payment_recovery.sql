-- Work is inserted in the same transaction as payment creation/verified outcomes.
CREATE TABLE payment_recovery (
 payment_id BIGINT NOT NULL PRIMARY KEY,
 action VARCHAR(16) NOT NULL DEFAULT 'query',
 status VARCHAR(16) NOT NULL DEFAULT 'pending',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 last_error VARCHAR(255) NULL,
 update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_payment_recovery_due(status,next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
