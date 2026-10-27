CREATE TABLE order_number_sequence (
 business_day CHAR(8) NOT NULL PRIMARY KEY, seq_value BIGINT NOT NULL
);
ALTER TABLE order_checkout_ticket ADD COLUMN token_hash CHAR(64) NULL;
CREATE UNIQUE INDEX uk_checkout_token ON order_checkout_ticket(token_hash);
ALTER TABLE `order` ADD COLUMN freight_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
ALTER TABLE `order` ADD COLUMN full_reduction_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
ALTER TABLE `order` ADD COLUMN coupon_discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0;

CREATE TABLE order_submission (
 order_no VARCHAR(64) NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL, request_key VARCHAR(64) NOT NULL,
 token_hash CHAR(64) NULL, request_hash CHAR(64) NOT NULL,
 owner VARCHAR(36) NOT NULL, phase VARCHAR(20) NOT NULL,
 lease_until DATETIME(6) NOT NULL, next_attempt_time DATETIME(6) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, last_error VARCHAR(255) NULL,
 UNIQUE KEY uk_submission_user_key(user_id,request_key),
 UNIQUE KEY uk_submission_token(token_hash),
 KEY idx_submission_recovery(phase,next_attempt_time)
);

-- Owned and transacted by the respective resource service, never by the order transaction.
-- A released tombstone fences out late reserve requests after uncertain RPC outcomes.
CREATE TABLE checkout_resource_fence (
 resource_type VARCHAR(20) NOT NULL, order_no VARCHAR(64) NOT NULL,
 status VARCHAR(20) NOT NULL, payload_hash CHAR(64) NULL,
 PRIMARY KEY(resource_type,order_no)
);
ALTER TABLE order_stock_reservation MODIFY COLUMN order_id BIGINT NULL;
ALTER TABLE order_stock_reservation ADD COLUMN order_no VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_stock_order_no_inventory ON order_stock_reservation(order_no,inventory_id);
CREATE TABLE checkout_coupon_reservation (
 order_no VARCHAR(64) NOT NULL PRIMARY KEY, user_id BIGINT NOT NULL,
 user_coupon_id BIGINT NULL, total_amount DECIMAL(10,2) NOT NULL,
 full_reduction_amount DECIMAL(10,2) NOT NULL, coupon_amount DECIMAL(10,2) NOT NULL,
 order_id BIGINT NULL
);

-- Marketing owns threshold discounts; no campaigns are fabricated or enabled by the migration.
CREATE TABLE checkout_full_reduction (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 minimum_amount DECIMAL(10,2) NOT NULL, discount_amount DECIMAL(10,2) NOT NULL,
 start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, enabled BOOLEAN NOT NULL DEFAULT TRUE
);
-- Address/delivery owns authoritative freight and reachability. '*' matches a region component.
CREATE TABLE checkout_delivery_rule (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 province VARCHAR(64) NOT NULL, city VARCHAR(64) NOT NULL, district VARCHAR(64) NOT NULL,
 reachable BOOLEAN NOT NULL, freight_amount DECIMAL(10,2) NOT NULL,
 UNIQUE KEY uk_delivery_region(province,city,district)
);
