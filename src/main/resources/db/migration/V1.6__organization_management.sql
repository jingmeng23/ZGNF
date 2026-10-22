ALTER TABLE store ADD COLUMN manager VARCHAR(100) NULL;
ALTER TABLE employee ADD COLUMN name VARCHAR(100) NULL;
ALTER TABLE employee ADD COLUMN store_id BIGINT NULL;
CREATE INDEX idx_employee_store ON employee(store_id);

-- 串行化组织关系和授权变更，保护最后一个管理员及关联删除检查。
CREATE TABLE management_lock (id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO management_lock(id) VALUES (1);

INSERT INTO permission(name, permission_key) VALUES
 ('门店查询', 'store:read'), ('门店维护', 'store:write'),
 ('员工查询', 'employee:read'), ('员工维护', 'employee:write'),
 ('角色权限查询', 'role:read'), ('角色权限维护', 'role:write');
INSERT INTO role_permission(role_id, permission_id)
 SELECT r.id, p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key = 'admin' AND p.permission_key IN
 ('store:read','store:write','employee:read','employee:write','role:read','role:write');
