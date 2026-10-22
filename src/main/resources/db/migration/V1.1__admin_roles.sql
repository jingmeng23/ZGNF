-- 商品与分类的关联校验共用事务锁，防止分类删除/停用与商品上架并发穿透。
CREATE TABLE catalog_lock (id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB;
INSERT INTO catalog_lock(id) VALUES (1);

CREATE TABLE employee_role (
 id BIGINT NOT NULL AUTO_INCREMENT,
 employee_id BIGINT NOT NULL,
 role_id BIGINT NOT NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_employee_role(employee_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO role(name, role_key, status) VALUES ('商城管理员', 'admin', 'active');
INSERT INTO permission(name, permission_key) VALUES
 ('商品查询', 'product:read'), ('商品维护', 'product:write'),
 ('分类查询', 'category:read'), ('分类维护', 'category:write');
INSERT INTO role_permission(role_id, permission_id)
 SELECT r.id, p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key = 'admin' AND p.permission_key IN
 ('product:read', 'product:write', 'category:read', 'category:write');
