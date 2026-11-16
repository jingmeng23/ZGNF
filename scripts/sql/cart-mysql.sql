-- MySQL 8.0+: select the target database before executing this script.
-- Standalone initialization only. Do not run before Flyway on a fresh application database.
-- Existing tables are not upgraded by CREATE TABLE IF NOT EXISTS.
CREATE TABLE IF NOT EXISTS cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '购物车明细ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID，来自服务端认证身份',
    product_id BIGINT NOT NULL COMMENT '商品ID',
    quantity INT NOT NULL COMMENT '购买数量，业务校验范围1至999',
    selected TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否勾选：0否，1是',
    price_snapshot DECIMAL(10,2) NULL COMMENT '加购或改数时的价格快照，仅用于展示及价格变化比较',
    product_name_snapshot VARCHAR(200) NULL COMMENT '商品名称快照，商品不可用时降级展示',
    cover_snapshot VARCHAR(512) NULL COMMENT '商品封面快照',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除：0正常，1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_product (user_id, product_id),
    KEY idx_cart_user (user_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户购物车明细';
