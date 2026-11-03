INSERT INTO permission(name, permission_key) VALUES
 ('用户查询', 'user:read'), ('用户维护', 'user:write');
INSERT INTO role_permission(role_id, permission_id)
SELECT r.id, p.id FROM role r JOIN permission p ON p.permission_key IN ('user:read','user:write')
WHERE r.role_key='admin';
