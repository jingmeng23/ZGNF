ALTER TABLE work_order ADD COLUMN employee_id BIGINT NULL;
ALTER TABLE work_order ADD COLUMN request_id VARCHAR(64) NULL;
ALTER TABLE work_order ADD COLUMN address_snapshot JSON NULL;
ALTER TABLE work_order ADD COLUMN appointment_time DATETIME NULL;
ALTER TABLE work_order ADD COLUMN accepted_time DATETIME NULL;
ALTER TABLE work_order ADD COLUMN started_time DATETIME NULL;
ALTER TABLE work_order ADD COLUMN completed_time DATETIME NULL;
ALTER TABLE work_order ADD COLUMN result VARCHAR(1000) NULL;
ALTER TABLE work_order_log ADD COLUMN operator_type VARCHAR(20) NULL;
CREATE UNIQUE INDEX uk_work_order_request ON work_order(user_id,request_id);
CREATE INDEX idx_work_order_employee ON work_order(employee_id,status);
INSERT INTO permission(name,permission_key) VALUES
 ('服务工单查询','workorder:read'),('服务工单派单','workorder:assign'),('服务工单处理','workorder:process');
INSERT INTO role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key='admin' AND p.permission_key IN ('workorder:read','workorder:assign','workorder:process');
