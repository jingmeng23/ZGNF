CREATE TABLE order_shipment (
 order_id BIGINT NOT NULL PRIMARY KEY,
 carrier VARCHAR(80) NOT NULL,
 tracking_no VARCHAR(100) NOT NULL,
 shipped_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE order_admin_operation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL,
 action VARCHAR(30) NOT NULL,
 remark VARCHAR(1000) NOT NULL,
 occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_order_admin_operation(order_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE INDEX idx_order_admin_status_time ON `order`(is_deleted,status,create_time,id);
