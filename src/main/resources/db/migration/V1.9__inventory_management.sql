ALTER TABLE inventory_log ADD COLUMN inventory_id BIGINT NULL;
ALTER TABLE inventory_log ADD COLUMN operator_id BIGINT NULL;
ALTER TABLE inventory_log ADD COLUMN request_id VARCHAR(64) NULL;
ALTER TABLE inventory_log ADD COLUMN requested_quantity INT NULL;
ALTER TABLE inventory_log ADD COLUMN quantity_before INT NULL;
ALTER TABLE inventory_log ADD COLUMN quantity_after INT NULL;
ALTER TABLE inventory_log ADD COLUMN locked_before INT NULL;
ALTER TABLE inventory_log ADD COLUMN locked_after INT NULL;
CREATE UNIQUE INDEX uk_inventory_log_request ON inventory_log(operator_id,request_id);
CREATE INDEX idx_inventory_log_inventory ON inventory_log(inventory_id,id);
INSERT INTO permission(name,permission_key) VALUES
 ('商品库存查询','inventory:read'),('商品库存维护','inventory:write');
INSERT INTO role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key='admin' AND p.permission_key IN ('inventory:read','inventory:write');
