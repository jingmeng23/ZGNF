CREATE TABLE order_status_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT NOT NULL,
 from_status VARCHAR(30) NULL,
 to_status VARCHAR(30) NOT NULL,
 occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_order_status_timeline(order_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Legacy orders get an honest snapshot, not an invented sequence of historical transitions.
INSERT INTO order_status_log(order_id,from_status,to_status,occurred_at)
 SELECT id,NULL,status,update_time FROM `order` WHERE is_deleted=0;
CREATE INDEX idx_order_user_tab ON `order`(user_id,is_deleted,status,create_time,id);
CREATE INDEX idx_order_user_all ON `order`(user_id,is_deleted,create_time,id);
