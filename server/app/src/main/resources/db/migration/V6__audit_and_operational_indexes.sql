CREATE TABLE audit_events (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, create_time BIGINT NOT NULL, trace_id VARCHAR(32) NOT NULL,
 actor_hash VARCHAR(64) NOT NULL, action VARCHAR(64) NOT NULL, resource_hash VARCHAR(64) NOT NULL,
 outcome VARCHAR(16) NOT NULL, http_status INT NOT NULL,
 INDEX ix_audit_time(create_time,id), INDEX ix_audit_trace(trace_id), CHECK(outcome IN('SUCCESS','FAILURE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
CREATE INDEX ix_users_role_id ON users(role,id);
CREATE INDEX ix_payments_status_order ON payments(status,order_id);
CREATE INDEX ix_refunds_status_order ON refunds(status,order_id);
CREATE INDEX ix_orders_seller_completed ON orders(owner_id,status,refunded_total,id);
