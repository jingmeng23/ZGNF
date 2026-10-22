-- 已领取优惠券保留面额和有效期快照，不依赖后续模板展示状态。
ALTER TABLE user_coupon ADD COLUMN name VARCHAR(100) NULL;
ALTER TABLE user_coupon ADD COLUMN amount DECIMAL(10,2) NULL;
ALTER TABLE user_coupon ADD COLUMN minimum_amount DECIMAL(10,2) NULL;
ALTER TABLE user_coupon ADD COLUMN start_time DATETIME NULL;
UPDATE user_coupon SET
 name=(SELECT c.name FROM coupon c WHERE c.id=user_coupon.coupon_id),
 amount=(SELECT c.amount FROM coupon c WHERE c.id=user_coupon.coupon_id),
 minimum_amount=(SELECT c.minimum_amount FROM coupon c WHERE c.id=user_coupon.coupon_id),
 start_time=(SELECT c.start_time FROM coupon c WHERE c.id=user_coupon.coupon_id);
UPDATE user_coupon SET expire_time=(SELECT c.end_time FROM coupon c WHERE c.id=user_coupon.coupon_id)
 WHERE expire_time IS NULL;
CREATE UNIQUE INDEX uk_user_coupon_claim ON user_coupon(user_id,coupon_id);
ALTER TABLE `order` ADD COLUMN user_coupon_id BIGINT NULL;
ALTER TABLE `order` ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0;
