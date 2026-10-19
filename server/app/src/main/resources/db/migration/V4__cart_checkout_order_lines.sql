CREATE TABLE categories (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
ALTER TABLE items ADD category_id BIGINT, ADD CONSTRAINT fk_item_category FOREIGN KEY(category_id) REFERENCES categories(id),
 ADD INDEX ix_item_category_status(category_id,status,id);
ALTER TABLE users ADD cart_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE checkout_batches (
 id VARCHAR(36) PRIMARY KEY, user_id VARCHAR(32) NOT NULL, idempotency_key VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL, total BIGINT NOT NULL, create_time BIGINT NOT NULL,
 CONSTRAINT fk_checkout_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT uq_checkout_retry UNIQUE(user_id,idempotency_key), CHECK(total>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
ALTER TABLE orders DROP CHECK ck_order_numbers,
 ADD CONSTRAINT ck_order_numbers CHECK(quantity>0 AND price>=0 AND total>=0),
 ADD checkout_id VARCHAR(36), ADD CONSTRAINT fk_order_checkout FOREIGN KEY(checkout_id) REFERENCES checkout_batches(id);
CREATE TABLE order_lines (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, item_id BIGINT NOT NULL,
 quantity INT NOT NULL, price INT NOT NULL, total BIGINT NOT NULL,
 item_name VARCHAR(128), item_description TEXT, snapshot_source VARCHAR(24) NOT NULL,
 CONSTRAINT fk_line_order FOREIGN KEY(order_id) REFERENCES orders(id),
 CONSTRAINT fk_line_item FOREIGN KEY(item_id) REFERENCES items(id), UNIQUE(order_id,item_id),
 CHECK(quantity>0 AND price>=0 AND total=CAST(quantity AS SIGNED)*CAST(price AS SIGNED)),
 INDEX ix_line_item(item_id,order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
INSERT INTO order_lines(order_id,item_id,quantity,price,total,item_name,item_description,snapshot_source)
 SELECT id,item_id,quantity,price,total,item_name,item_description,snapshot_source FROM orders;
CREATE TABLE cart_entries (
 user_id VARCHAR(32) NOT NULL, item_id BIGINT NOT NULL, quantity INT NOT NULL, selected BOOLEAN NOT NULL,
 PRIMARY KEY(user_id,item_id), CONSTRAINT fk_cart_user FOREIGN KEY(user_id) REFERENCES users(id),
 CONSTRAINT fk_cart_item FOREIGN KEY(item_id) REFERENCES items(id), CHECK(quantity>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
