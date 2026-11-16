-- Optional local development data. Apply only after confirming this delivery policy:
-- Tianjin / Tianjin City / Hedong District (120000 / 120100 / 120102), reachable, CNY 0.00 freight.
-- Names come from the server's authoritative regions; existing rules are preserved.
-- This file is not a Flyway migration and is never applied automatically.
START TRANSACTION;

INSERT INTO checkout_delivery_rule(province,city,district,reachable,freight_amount)
SELECT province.name,city.name,district.name,TRUE,0.00
FROM address_region province
JOIN address_region city ON city.parent_code=province.code AND city.region_level=2 AND city.active=TRUE
JOIN address_region district ON district.parent_code=city.code AND district.region_level=3 AND district.active=TRUE
WHERE province.code='120000' AND province.region_level=1 AND province.active=TRUE
  AND city.code='120100' AND district.code='120102'
  AND NOT EXISTS (
    SELECT 1 FROM checkout_delivery_rule existing
    WHERE existing.province=province.name AND existing.city=city.name AND existing.district=district.name
  );

SELECT rule.id,rule.province,rule.city,rule.district,rule.reachable,rule.freight_amount
FROM checkout_delivery_rule rule
JOIN address_region province ON province.code='120000' AND province.name=rule.province
JOIN address_region city ON city.code='120100' AND city.name=rule.city
JOIN address_region district ON district.code='120102' AND district.name=rule.district;

COMMIT;
