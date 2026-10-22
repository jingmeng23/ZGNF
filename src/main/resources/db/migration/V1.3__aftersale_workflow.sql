ALTER TABLE aftersale ADD COLUMN request_id VARCHAR(64) NULL;
ALTER TABLE aftersale ADD COLUMN review_remark VARCHAR(500) NULL;
ALTER TABLE aftersale ADD COLUMN reviewed_by BIGINT NULL;
ALTER TABLE aftersale ADD COLUMN refund_reference VARCHAR(100) NULL;
ALTER TABLE aftersale ADD COLUMN completion_remark VARCHAR(500) NULL;
ALTER TABLE aftersale ADD COLUMN completed_time DATETIME NULL;
CREATE UNIQUE INDEX uk_aftersale_user_request ON aftersale(user_id, request_id);
CREATE INDEX idx_aftersale_order_status ON aftersale(order_id, status, is_deleted);

INSERT INTO permission(name, permission_key) VALUES
 ('售后查询', 'aftersale:read'), ('售后处理', 'aftersale:write');
INSERT INTO role_permission(role_id, permission_id)
 SELECT r.id, p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key='admin' AND p.permission_key IN ('aftersale:read', 'aftersale:write');
