ALTER TABLE cart_item ADD COLUMN price_snapshot DECIMAL(10,2) NULL;
ALTER TABLE cart_item ADD COLUMN product_name_snapshot VARCHAR(200) NULL;
ALTER TABLE cart_item ADD COLUMN cover_snapshot VARCHAR(512) NULL;
UPDATE cart_item SET price_snapshot=(SELECT p.price FROM product p WHERE p.id=cart_item.product_id)
WHERE price_snapshot IS NULL AND EXISTS (SELECT 1 FROM product p WHERE p.id=cart_item.product_id);
UPDATE cart_item SET product_name_snapshot=(SELECT p.name FROM product p WHERE p.id=cart_item.product_id),
 cover_snapshot=(SELECT p.cover_url FROM product p WHERE p.id=cart_item.product_id)
WHERE product_name_snapshot IS NULL AND EXISTS (SELECT 1 FROM product p WHERE p.id=cart_item.product_id);
