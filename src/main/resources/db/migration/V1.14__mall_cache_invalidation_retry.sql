CREATE TABLE mall_detail_invalidation_retry (
 product_id BIGINT NOT NULL PRIMARY KEY,
 requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY idx_detail_invalidation_retry_time(requested_at)
);
