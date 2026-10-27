CREATE TABLE order_close_release (
 order_id BIGINT NOT NULL PRIMARY KEY,
 step INT NOT NULL DEFAULT 0,
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 last_error VARCHAR(255),
 KEY idx_close_release_due(step,next_attempt_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_order_expiry_scan ON `order`(status,is_deleted,expire_time,id);
