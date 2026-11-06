ALTER TABLE user_address ADD COLUMN coordinate_system VARCHAR(16) NOT NULL DEFAULT 'WGS84';
ALTER TABLE user_address ADD COLUMN adcode VARCHAR(12) NULL;
UPDATE user_address SET adcode = district_code;
