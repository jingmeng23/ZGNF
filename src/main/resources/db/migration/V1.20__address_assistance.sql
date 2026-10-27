ALTER TABLE user_address ADD COLUMN label VARCHAR(20) NULL;
ALTER TABLE user_address ADD COLUMN province_code VARCHAR(12) NULL;
ALTER TABLE user_address ADD COLUMN city_code VARCHAR(12) NULL;
ALTER TABLE user_address ADD COLUMN district_code VARCHAR(12) NULL;
ALTER TABLE user_address ADD COLUMN latitude DECIMAL(10,7) NULL;
ALTER TABLE user_address ADD COLUMN longitude DECIMAL(10,7) NULL;
CREATE TABLE address_region (
 code VARCHAR(12) NOT NULL PRIMARY KEY,
 parent_code VARCHAR(12) NULL,
 name VARCHAR(64) NOT NULL,
 region_level INT NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 replacement_code VARCHAR(12) NULL,
 version VARCHAR(64) NOT NULL,
 KEY idx_address_region_parent(parent_code,active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Import authoritative region data through the administrator import API; do not invent a national dataset.
