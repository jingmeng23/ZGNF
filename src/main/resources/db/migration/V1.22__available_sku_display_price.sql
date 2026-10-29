-- Display a purchasable SKU price when stock exists; retain a reference price
-- for entirely sold-out products and the SPU price for products without active SKUs.
CREATE OR REPLACE VIEW product_published_price AS
SELECT p.id AS product_id,
       COALESCE(
           (SELECT MIN(q.price)
            FROM product_sku s
            JOIN sku_published_price q ON q.sku_id = s.id
            JOIN sku_available_stock a ON a.sku_id = s.id
            WHERE s.product_id = p.id AND s.status = 'active' AND a.available > 0),
           (SELECT MIN(q.price)
            FROM product_sku s
            JOIN sku_published_price q ON q.sku_id = s.id
            WHERE s.product_id = p.id AND s.status = 'active'),
           p.price
       ) AS price
FROM product p;
