INSERT INTO permission(name,permission_key) VALUES
 ('经营统计查询','statistics:read'),('经营报表导出','statistics:export');
INSERT INTO role_permission(role_id,permission_id)
 SELECT r.id,p.id FROM role r CROSS JOIN permission p
 WHERE r.role_key='admin' AND p.permission_key IN ('statistics:read','statistics:export');
CREATE INDEX idx_statistics_order_created ON `order`(is_deleted,create_time);
CREATE INDEX idx_statistics_payment_paid ON payment(is_deleted,status,paid_time,order_id);
CREATE INDEX idx_statistics_refund_completed ON aftersale(is_deleted,status,completed_time);
CREATE INDEX idx_statistics_user_created ON `user`(is_deleted,create_time);
CREATE INDEX idx_statistics_work_created ON work_order(is_deleted,create_time,store_id);
CREATE INDEX idx_statistics_work_completed ON work_order(is_deleted,status,completed_time,store_id);
