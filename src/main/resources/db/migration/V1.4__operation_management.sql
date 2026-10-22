INSERT INTO permission(name, permission_key) VALUES
 ('运营数据查询', 'operation:read'), ('运营数据维护', 'operation:write');
INSERT INTO role_permission(role_id, permission_id)
 SELECT r.id, p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key='admin' AND p.permission_key IN ('operation:read', 'operation:write');
