-- Product-center owned SKU/content data; the mall only reads these records.
CREATE TABLE product_sku (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL,
 code VARCHAR(100) NOT NULL, name VARCHAR(200) NOT NULL, image_url VARCHAR(512),
 price DECIMAL(10,2) NOT NULL, original_price DECIMAL(10,2), status VARCHAR(20) NOT NULL DEFAULT 'active',
 UNIQUE KEY uk_sku_code(code), KEY idx_sku_product(product_id,status)
);
CREATE TABLE product_sku_spec (
 sku_id BIGINT NOT NULL, dimension VARCHAR(64) NOT NULL, spec_value VARCHAR(100) NOT NULL,
 PRIMARY KEY(sku_id,dimension)
);
CREATE TABLE product_attribute (
 product_id BIGINT NOT NULL, attribute_name VARCHAR(64) NOT NULL, attribute_value VARCHAR(100) NOT NULL,
 PRIMARY KEY(product_id,attribute_name,attribute_value)
);
CREATE TABLE product_media (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, url VARCHAR(512) NOT NULL,
 sort INT NOT NULL DEFAULT 0, KEY idx_media_product(product_id,sort)
);
CREATE TABLE product_promise (
 product_id BIGINT NOT NULL, promise VARCHAR(200) NOT NULL, PRIMARY KEY(product_id,promise)
);
-- Published review aggregate, maintained by the review owner rather than the mall.
CREATE TABLE product_review_summary (
 product_id BIGINT NOT NULL PRIMARY KEY, review_count BIGINT NOT NULL DEFAULT 0,
 positive_count BIGINT NOT NULL DEFAULT 0, average_rating DECIMAL(3,2) NOT NULL DEFAULT 0
);
-- Inventory-service owned SKU balances. Existing SPU balances are not copied to SKUs.
CREATE TABLE sku_inventory (
 sku_id BIGINT NOT NULL, warehouse_id BIGINT NOT NULL DEFAULT 0,
 quantity INT NOT NULL DEFAULT 0, locked_quantity INT NOT NULL DEFAULT 0,
 PRIMARY KEY(sku_id,warehouse_id)
);
-- Marketing-service published final quotes; no discount formula is implemented here.
CREATE TABLE marketing_sku_quote (
 sku_id BIGINT NOT NULL PRIMARY KEY, price DECIMAL(10,2) NOT NULL,
 starts_at DATETIME NOT NULL, ends_at DATETIME NOT NULL
);
CREATE TABLE mall_favorite (
 user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(user_id,product_id), KEY idx_favorite_user_time(user_id,created_at)
);
CREATE TABLE mall_history (
 user_id BIGINT NOT NULL, product_id BIGINT NOT NULL, viewed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(user_id,product_id), KEY idx_history_user_time(user_id,viewed_at)
);
CREATE VIEW sku_available_stock AS
 SELECT sku_id, SUM(GREATEST(0,quantity-locked_quantity)) available FROM sku_inventory GROUP BY sku_id;
CREATE VIEW product_available_stock AS
 SELECT p.id product_id,
 CASE WHEN EXISTS(SELECT 1 FROM product_sku s WHERE s.product_id=p.id)
 THEN COALESCE((SELECT SUM(a.available) FROM product_sku s JOIN sku_available_stock a ON a.sku_id=s.id
 WHERE s.product_id=p.id AND s.status='active'),0)
 ELSE COALESCE((SELECT SUM(GREATEST(0,i.quantity-i.locked_quantity)) FROM inventory i WHERE i.product_id=p.id AND i.is_deleted=0),0)
 END available FROM product p;
CREATE VIEW sku_published_price AS
 SELECT s.id sku_id, COALESCE(q.price,s.price) price FROM product_sku s
 LEFT JOIN marketing_sku_quote q ON q.sku_id=s.id AND q.starts_at<=CURRENT_TIMESTAMP AND q.ends_at>CURRENT_TIMESTAMP;
CREATE VIEW product_published_price AS
 SELECT p.id product_id, COALESCE((SELECT MIN(q.price) FROM product_sku s JOIN sku_published_price q ON q.sku_id=s.id
 WHERE s.product_id=p.id AND s.status='active'),p.price) price FROM product p;
