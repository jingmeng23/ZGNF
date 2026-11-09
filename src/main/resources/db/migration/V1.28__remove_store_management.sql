-- Retain stock row IDs, quantities and reservations. Former location rows become
-- independent stock records in the shared mall inventory, so existing orders
-- still release/deduct the exact inventory IDs they reserved.
ALTER TABLE inventory DROP INDEX uk_inventory_product_store;
ALTER TABLE inventory DROP INDEX idx_inventory_store;
ALTER TABLE inventory DROP COLUMN store_id;
CREATE INDEX idx_inventory_product ON inventory(product_id,is_deleted,id);
ALTER TABLE inventory_log DROP COLUMN store_id;

ALTER TABLE employee DROP INDEX idx_employee_store;
ALTER TABLE employee DROP COLUMN store_id;
ALTER TABLE work_order DROP INDEX idx_work_order_status;
ALTER TABLE work_order DROP INDEX idx_statistics_work_created;
ALTER TABLE work_order DROP INDEX idx_statistics_work_completed;
ALTER TABLE work_order DROP COLUMN store_id;
CREATE INDEX idx_work_order_status ON work_order(status);
CREATE INDEX idx_statistics_work_created ON work_order(is_deleted,create_time);
CREATE INDEX idx_statistics_work_completed ON work_order(is_deleted,status,completed_time);

DELETE FROM role_permission WHERE permission_id IN
 (SELECT id FROM permission WHERE permission_key IN ('store:read','store:write'));
DELETE FROM permission WHERE permission_key IN ('store:read','store:write');
DELETE FROM operation_log WHERE request_uri LIKE '/api/v1/admin/stores%'
 OR request_uri LIKE '/api/v1/work-orders/stores%'
 OR request_uri LIKE '/api/v1/admin/statistics/stores%';
UPDATE work_order_log SET content=REGEXP_REPLACE(content,'(原门店|新门店)=[^,;]*, ','')
 WHERE action='assigned' AND (content LIKE '%原门店=%' OR content LIKE '%新门店=%');
DROP TABLE store;
