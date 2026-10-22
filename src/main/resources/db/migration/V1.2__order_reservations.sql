ALTER TABLE `order` ADD COLUMN request_id VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_order_user_request ON `order`(user_id, request_id);

-- Persist the exact stock rows for reliable release and deduction.
CREATE TABLE order_stock_reservation (
 id BIGINT NOT NULL AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 inventory_id BIGINT NOT NULL,
 product_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'reserved',
 PRIMARY KEY(id),
 UNIQUE KEY uk_order_inventory(order_id, inventory_id),
 KEY idx_order_reservation(order_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO permission(name, permission_key) VALUES
 ('订单查询', 'order:read'), ('订单处理', 'order:write');
INSERT INTO role_permission(role_id, permission_id)
 SELECT r.id, p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key='admin' AND p.permission_key IN ('order:read', 'order:write');
