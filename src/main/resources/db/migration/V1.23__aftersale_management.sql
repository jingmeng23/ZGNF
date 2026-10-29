CREATE TABLE aftersale_operation (
 id BIGINT NOT NULL AUTO_INCREMENT,
 aftersale_id BIGINT NOT NULL,
 operator_id BIGINT NOT NULL,
 operator_type VARCHAR(16) NOT NULL,
 action VARCHAR(24) NOT NULL,
 from_status VARCHAR(20) NULL,
 to_status VARCHAR(20) NOT NULL,
 remark VARCHAR(500) NOT NULL,
 occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(id), KEY idx_aftersale_operation(aftersale_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
