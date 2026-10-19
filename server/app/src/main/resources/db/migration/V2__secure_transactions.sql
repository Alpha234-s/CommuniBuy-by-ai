-- Apply only after the documented legacy audit and backup. MySQL DDL is not transactional.
ALTER TABLE users CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE items CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE orders CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE blacklist CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
ALTER TABLE users ADD auth_version BIGINT NOT NULL DEFAULT 0,
 ADD password_scheme VARCHAR(16) NOT NULL DEFAULT 'LEGACY_PLAIN';
UPDATE users SET role = CASE WHEN role < 0 THEN -1 WHEN role > 0 THEN 1 ELSE 0 END;
ALTER TABLE users MODIFY id VARCHAR(32) NOT NULL, MODIFY role INT NOT NULL DEFAULT 0,
 ADD CONSTRAINT ck_user_role CHECK (role IN (-1,0,1)),
 ADD CONSTRAINT ck_user_id CHECK (REGEXP_LIKE(id, '^[A-Za-z_][A-Za-z0-9_]{0,31}$', 'c'));
ALTER TABLE items ADD shipment_count BIGINT NOT NULL DEFAULT 0, ADD version BIGINT NOT NULL DEFAULT 0;
UPDATE items SET shipment_count = GREATEST(status,0), status = CASE WHEN status < 0 THEN -1 ELSE 0 END,
 start_time = GREATEST(COALESCE(start_time,0),0), end_time = CASE WHEN end_time < 0 THEN NULL ELSE end_time END;
ALTER TABLE items MODIFY id BIGINT NOT NULL AUTO_INCREMENT, MODIFY name VARCHAR(128) NOT NULL, MODIFY price INT NOT NULL,
 MODIFY stock INT NOT NULL, MODIFY owner_id VARCHAR(32) NOT NULL, MODIFY start_time BIGINT NOT NULL,
 ADD CONSTRAINT fk_item_owner FOREIGN KEY (owner_id) REFERENCES users(id),
 ADD CONSTRAINT ck_item_numbers CHECK (price >= 0 AND stock >= 0 AND shipment_count >= 0),
 ADD CONSTRAINT ck_item_state CHECK (status IN (-1,0)),
 ADD CONSTRAINT ck_item_time CHECK (start_time >= 0 AND (end_time IS NULL OR end_time > start_time)),
 ADD INDEX ix_items_owner_status (owner_id,status,id), ADD INDEX ix_items_activity (status,start_time,end_time);
ALTER TABLE orders ADD total BIGINT NOT NULL DEFAULT 0, ADD expires_at BIGINT NOT NULL DEFAULT 0,
 ADD idempotency_key VARCHAR(64), ADD request_hash VARCHAR(64), ADD refund_status VARCHAR(24) NOT NULL DEFAULT 'NONE';
UPDATE orders SET total = CAST(price AS SIGNED) * CAST(quantity AS SIGNED),
 expires_at = create_time + 900, idempotency_key = CONCAT('legacy-',id), request_hash = SHA2(CONCAT('legacy-',id),256);
ALTER TABLE orders MODIFY id BIGINT NOT NULL AUTO_INCREMENT, MODIFY item_id BIGINT NOT NULL,
 MODIFY user_id VARCHAR(32) NOT NULL, MODIFY owner_id VARCHAR(32) NOT NULL,
 MODIFY price INT NOT NULL, MODIFY quantity INT NOT NULL, MODIFY address VARCHAR(255) NOT NULL,
 MODIFY create_time BIGINT NOT NULL, MODIFY idempotency_key VARCHAR(64) NOT NULL, MODIFY request_hash VARCHAR(64) NOT NULL,
 ADD CONSTRAINT fk_order_item FOREIGN KEY (item_id) REFERENCES items(id),
 ADD CONSTRAINT fk_order_buyer FOREIGN KEY (user_id) REFERENCES users(id),
 ADD CONSTRAINT fk_order_seller FOREIGN KEY (owner_id) REFERENCES users(id),
 ADD CONSTRAINT uq_order_retry UNIQUE (user_id,idempotency_key),
 ADD CONSTRAINT ck_order_numbers CHECK (quantity > 0 AND price >= 0 AND total = CAST(price AS SIGNED) * CAST(quantity AS SIGNED)),
 ADD CONSTRAINT ck_order_state CHECK (status IN (-1,0,1,2,3)),
 ADD INDEX ix_order_buyer (user_id,status,id), ADD INDEX ix_order_seller (owner_id,status,id),
 ADD INDEX ix_order_item (item_id,status,id), ADD INDEX ix_order_expiry (status,expires_at,id);
-- Legacy mock tokens are invalid after upgrade. Keep legacy hashes as inert revocations.
ALTER TABLE blacklist DROP PRIMARY KEY, DROP COLUMN id, MODIFY token_hash VARCHAR(64) NOT NULL,
 ADD PRIMARY KEY (token_hash), ADD INDEX ix_blacklist_expiry (expire_at);
