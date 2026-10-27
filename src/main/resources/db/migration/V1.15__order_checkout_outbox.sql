CREATE TABLE order_outbox (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_no VARCHAR(64) NOT NULL,
 event_type VARCHAR(32) NOT NULL,
 status VARCHAR(16) NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_time DATETIME NOT NULL,
 sent_time DATETIME NULL,
 last_error VARCHAR(255) NULL,
 UNIQUE KEY uk_order_event(order_no,event_type),
 KEY idx_order_outbox_retry(status,next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_checkout_ticket (
 order_no VARCHAR(64) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 fingerprint VARCHAR(64) NOT NULL,
 expire_time DATETIME NOT NULL,
 KEY idx_checkout_expiry(expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE order_item ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
ALTER TABLE order_item ADD COLUMN pay_amount DECIMAL(10,2) NULL;
UPDATE order_item SET pay_amount=amount WHERE pay_amount IS NULL;
