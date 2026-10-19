ALTER TABLE orders ADD refunded_total BIGINT NOT NULL DEFAULT 0, ADD CHECK(refunded_total>=0 AND refunded_total<=total);
CREATE TABLE payments (
 id VARCHAR(36) PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, merchant VARCHAR(64) NOT NULL,
 amount BIGINT NOT NULL, channel VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL,
 provider_reference VARCHAR(96) UNIQUE, create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 FOREIGN KEY(order_id) REFERENCES orders(id), CHECK(amount>=0),
 CHECK(status IN('PENDING','SUCCEEDED','FAILED','CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE refunds (
 id VARCHAR(36) PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, payment_id VARCHAR(36) NOT NULL,
 amount BIGINT NOT NULL, reason VARCHAR(255) NOT NULL, status VARCHAR(24) NOT NULL,
 attempts INT NOT NULL DEFAULT 0, provider_reference VARCHAR(96) UNIQUE,
 create_time BIGINT NOT NULL, update_time BIGINT NOT NULL,
 FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(payment_id) REFERENCES payments(id),
 CHECK(amount>=0 AND attempts>=0), CHECK(status IN('REQUESTED','REJECTED','APPROVED','FAILED','SUCCEEDED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE channel_events (
 kind VARCHAR(16) NOT NULL, event_id VARCHAR(64) NOT NULL, resource_id VARCHAR(36) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, create_time BIGINT NOT NULL, PRIMARY KEY(kind,event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE simulator_operations (
 operation_id VARCHAR(64) PRIMARY KEY, amount BIGINT NOT NULL, status VARCHAR(24) NOT NULL, reference VARCHAR(96) NOT NULL, merchant VARCHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE finance_mutex (id INT PRIMARY KEY);
INSERT INTO finance_mutex(id) VALUES(1);
CREATE TABLE reviews (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, item_id BIGINT NOT NULL,
 user_id VARCHAR(32) NOT NULL, rating INT NOT NULL, comment VARCHAR(1000) NOT NULL, create_time BIGINT NOT NULL,
 UNIQUE(order_id,item_id), FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(item_id) REFERENCES items(id),
 FOREIGN KEY(user_id) REFERENCES users(id), CHECK(rating BETWEEN 1 AND 5), INDEX ix_review_item(item_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE settlement_batches (
 id VARCHAR(36) PRIMARY KEY, seller_id VARCHAR(32) NOT NULL, idempotency_key VARCHAR(64) NOT NULL,
 net_amount BIGINT NOT NULL, create_time BIGINT NOT NULL, UNIQUE(seller_id,idempotency_key),
 FOREIGN KEY(seller_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE TABLE settlement_entries (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, batch_id VARCHAR(36), seller_id VARCHAR(32) NOT NULL,
 order_id BIGINT NOT NULL, refund_id VARCHAR(36), kind VARCHAR(16) NOT NULL, amount BIGINT NOT NULL,
 sale_order_id BIGINT GENERATED ALWAYS AS (CASE WHEN kind='SALE' THEN order_id ELSE NULL END) STORED,
 UNIQUE(sale_order_id), UNIQUE(refund_id), FOREIGN KEY(batch_id) REFERENCES settlement_batches(id),
 FOREIGN KEY(seller_id) REFERENCES users(id), FOREIGN KEY(order_id) REFERENCES orders(id), FOREIGN KEY(refund_id) REFERENCES refunds(id),
 CHECK((kind='SALE' AND amount>=0 AND refund_id IS NULL) OR(kind='REFUND' AND amount<=0 AND refund_id IS NOT NULL)),
 INDEX ix_settlement_pending(seller_id,batch_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
